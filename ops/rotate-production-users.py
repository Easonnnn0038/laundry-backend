#!/usr/bin/env python3
"""Run interactively as root on the production host; never pass passwords as arguments."""

import getpass
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time

import bcrypt


EXPECTED = {"admin": "ADMIN", "employee1": "EMPLOYEE", "employee2": "EMPLOYEE"}
BACKUP_DIR = Path("/var/backups/laundry-mysql")


def mysql():
    return subprocess.Popen(
        ["mysql", "--batch", "--raw", "--skip-column-names", "--unbuffered", "laundry_db"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, bufsize=1,
    )


def query(process, statement):
    marker = "ROTATION_END_" + secrets.token_hex(8)
    process.stdin.write(statement.rstrip(";\n") + f";\nSELECT '{marker}';\n")
    process.stdin.flush()
    rows = []
    for line in process.stdout:
        value = line.rstrip("\r\n")
        if value == marker:
            return rows
        rows.append(value)
    raise RuntimeError("MySQL 命令失败：" + process.stderr.read().strip()[-300:])


def check_backup():
    backups = sorted(BACKUP_DIR.glob("laundry_db-*.sql.gz"), key=lambda path: path.stat().st_mtime, reverse=True)
    if not backups or time.time() - backups[0].stat().st_mtime > 36 * 3600:
        raise RuntimeError("没有近 36 小时的数据库备份，已停止")
    subprocess.run(["gzip", "-t", str(backups[0])], check=True, capture_output=True)
    print("已验证备份：", backups[0].name)


def read_accounts():
    names = {}
    hashes = {}
    passwords = set()
    for old in EXPECTED:
        while True:
            new = input(f"{old} 的新登录名（6～20 位英文字母/数字/下划线，字母开头）：").strip()
            if re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{5,19}", new) and new not in EXPECTED and new not in names.values():
                names[old] = new
                break
            print("登录名格式不正确、与旧名相同或与本次其他新名重复")
        while True:
            password = getpass.getpass(f"{new} 的新密码（16～30 字符、最多 72 字节，不回显）：")
            encoded = password.encode("utf-8")
            if not 16 <= len(password) <= 30 or len(encoded) > 72 or password in passwords:
                print("密码长度不符合要求，或与本次其他账号重复")
                continue
            if getpass.getpass("再次输入新密码：") != password:
                print("两次密码不一致")
                continue
            passwords.add(password)
            hashes[old] = bcrypt.hashpw(encoded, bcrypt.gensalt(rounds=12)).decode("ascii")
            break
    return names, hashes


def self_test():
    process = mysql()
    try:
        query(process, "CREATE TEMPORARY TABLE rotation_probe(id INT PRIMARY KEY, password VARCHAR(255)) ENGINE=InnoDB")
        query(process, "START TRANSACTION")
        assert query(process, "INSERT INTO rotation_probe(id) VALUES (1); SELECT ROW_COUNT()") == ["1"]
        assert query(process, "UPDATE rotation_probe SET id=2 WHERE id=1; SELECT ROW_COUNT()") == ["1"]
        hashed = bcrypt.hashpw(b"rotation-test-password", bcrypt.gensalt(rounds=12)).decode("ascii")
        assert query(process, f"UPDATE rotation_probe SET password='{hashed}' WHERE id=2; SELECT ROW_COUNT()") == ["1"]
        assert bcrypt.checkpw(b"rotation-test-password", query(process, "SELECT password FROM rotation_probe WHERE id=2")[0].encode("ascii"))
        query(process, "ROLLBACK")
        assert query(process, "SELECT COUNT(*) FROM rotation_probe") == ["0"]
        print("临时表事务自检通过；正式账号未修改")
    finally:
        process.stdin.close()
        process.wait(timeout=5)


def verify_password():
    if not sys.stdin.isatty():
        raise RuntimeError("需要交互式终端输入密码")
    username = input("要核验的新登录名：").strip()
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{5,31}", username):
        raise RuntimeError("登录名格式不正确")
    process = mysql()
    try:
        rows = query(process, f"SELECT password FROM users WHERE username='{username}' AND status=1")
        if len(rows) != 1:
            raise RuntimeError("未找到启用中的账号")
        password = getpass.getpass("请输入该账号密码（不回显）：")
        print("密码匹配" if bcrypt.checkpw(password.encode("utf-8"), rows[0].encode("ascii")) else "密码不匹配")
    finally:
        process.stdin.close()
        process.wait(timeout=5)


def reset_password():
    if not sys.stdin.isatty():
        raise RuntimeError("需要交互式终端输入密码")
    check_backup()
    username = input("要重设密码的新登录名：").strip()
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{5,31}", username):
        raise RuntimeError("登录名格式不正确")
    process = mysql()
    try:
        rows = query(process, f"SELECT id,role,status FROM users WHERE username='{username}'")
        if len(rows) != 1 or rows[0].split("\t")[2] != "1":
            raise RuntimeError("未找到启用中的账号")
        password = getpass.getpass("新密码（16～30 字符、最多 72 字节，不回显）：")
        if not 16 <= len(password) <= 30 or len(password.encode("utf-8")) > 72:
            raise RuntimeError("密码长度不符合要求")
        if getpass.getpass("再次输入新密码：") != password:
            raise RuntimeError("两次密码不一致，未修改账号")
        hashed = bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=12)).decode("ascii")
        if input(f"确认重设 {username} 的密码？输入 RESET：").strip() != "RESET":
            print("已取消，未修改账号")
            return
        query(process, "START TRANSACTION")
        if query(process, f"SELECT id,role,status FROM users WHERE username='{username}' FOR UPDATE") != rows:
            raise RuntimeError("账号状态发生变化，已回滚")
        user_id = int(rows[0].split("\t")[0])
        if query(process, f"UPDATE users SET password='{hashed}',update_time=NOW() WHERE id={user_id}; SELECT ROW_COUNT()") != ["1"]:
            raise RuntimeError("密码更新笔数不正确，已回滚")
        stored = query(process, f"SELECT password FROM users WHERE username='{username}'")
        if stored != [hashed] or not bcrypt.checkpw(password.encode("utf-8"), stored[0].encode("ascii")):
            raise RuntimeError("写入校验失败，已回滚")
        query(process, "COMMIT")
        print("密码已重设，数据库校验通过。请再运行 --verify 独立核验。")
    finally:
        if process.poll() is None:
            try:
                query(process, "ROLLBACK")
            except Exception:
                pass
            process.stdin.close()
            process.wait(timeout=5)


def run():
    if os.geteuid() != 0:
        raise RuntimeError("必须在服务器上以 root 身份运行")
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    if sys.argv[1:] == ["--verify"]:
        verify_password()
        return
    if sys.argv[1:] == ["--reset-password"]:
        reset_password()
        return
    check_backup()
    process = mysql()
    try:
        current = query(process, "SELECT id,username,role,status FROM users ORDER BY id")
        accounts = [row.split("\t") for row in current]
        if len(accounts) != 3 or {row[1]: row[2] for row in accounts} != EXPECTED or any(row[3] != "1" for row in accounts):
            raise RuntimeError("正式库账号清单与预期不符，未修改任何账号")
        print("将轮换：admin、employee1、employee2；所有人需重新登录。")
        if sys.argv[1:] == ["--check"]:
            print("只读检查通过，未修改账号")
            return
        if not sys.stdin.isatty():
            raise RuntimeError("需要交互式终端输入密码；不要通过聊天或命令参数传密码")
        names, hashes = read_accounts()
        print("将一次性改名并换密：" + "，".join(f"{old} → {names[old]}" for old in EXPECTED))
        if input("确认执行？输入 ROTATE：").strip() != "ROTATE":
            print("已取消，未修改账号")
            return

        query(process, "START TRANSACTION")
        locked = query(process, "SELECT id,username,role,status FROM users ORDER BY id FOR UPDATE")
        if locked != current:
            raise RuntimeError("账号在输入期间发生变化，已回滚")
        quoted = ",".join(f"'{name}'" for name in names.values())
        if query(process, f"SELECT COUNT(*) FROM users WHERE username IN ({quoted})") != ["0"]:
            raise RuntimeError("新登录名已被占用，已回滚")
        for row in accounts:
            user_id, old, role, _ = row
            changed = query(process, f"""
                UPDATE users SET username='{names[old]}',password='{hashes[old]}',update_time=NOW()
                WHERE id={int(user_id)} AND username='{old}' AND role='{role}' AND status=1;
                SELECT ROW_COUNT()
            """)
            if changed != ["1"]:
                raise RuntimeError("账号更新笔数不正确，已回滚")
        new_rows = query(process, "SELECT username,role,status FROM users ORDER BY id")
        if new_rows != [f"{names[row[1]]}\t{row[2]}\t1" for row in accounts]:
            raise RuntimeError("更新结果核验失败，已回滚")
        query(process, "COMMIT")
        print("三个账号已更新。旧用户名不能再登录，原有登录令牌也需重新登录。")
    finally:
        if process.poll() is None:
            try:
                query(process, "ROLLBACK")
            except Exception:
                pass
            process.stdin.close()
            process.wait(timeout=5)


if __name__ == "__main__":
    try:
        run()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print("操作停止：", error, file=sys.stderr)
        sys.exit(1)
