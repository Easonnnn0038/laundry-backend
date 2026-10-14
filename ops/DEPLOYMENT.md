# 生产部署与备份

## 启动前

生产环境设置 `SPRING_PROFILES_ACTIVE=prod`，再按 `production.env.example` 提供所有变量。不要把真实 `.env`、JWT 密钥、数据库密码、RabbitMQ 密码或设备令牌提交到 Git。

门店后端是数据库迁移的唯一执行者。现有数据库首次启动会基线为 V7 并执行 V8；工厂后端不得单独维护 Flyway 版本。

## V23/V24 上线门槛

1. 先确认正式小程序主体、已备案 HTTPS 域名与隐私政策，确认客户财务/法务对交易凭证中个人信息的保留范围；未确认前不要开放“清理到期资料”正式操作。
2. 停止业务写入，完成数据库及照片备份，核对备份 SHA256，并在**与正式库隔离**的恢复库验证可恢复。保留迁移前备份；V23/V24 的 DDL 不提供自动回滚，故障时按恢复方案处理，不能靠手工反向改表。
3. 在恢复库上先运行新版门店后端，让 Flyway 从当前版本连续迁移到 V24，核对 `flyway_schema_history` 的 V23/V24 均成功，并核对原有订单、会员卡和收款笔数及金额。工厂后端须在门店迁移成功后启动。
4. 用独立测试身份走“申请删除 → 管理员核验并注销 → 未满半年不清理 → 满半年后人工清理”的回归；不得在正式库回填或修改真实订单完成时间。确认旧小程序令牌失效、操作审计存在、交易金额不变。
5. 才部署门店/工厂网页和后端，确认 `SPRING_PROFILES_ACTIVE=prod`，测试登录关闭，正式微信密钥仅由受限环境变量提供。发布后跑门店—工厂主流程、备份/告警检查和小程序真机授权。旧小程序令牌在 V23 上线后需重新登录。

本机无客户数据的开发库预演可运行 `ops/e2e-regression.ps1`；它只复制本机 `laundry_db` 的表结构和基础配置到 `laundry_e2e_test`，并在完成后删除自己创建的测试库，**不能替代正式库备份恢复预演**。若同名测试库已存在，脚本会拒绝覆盖。

### 公网账号轮换

正式库当前如仍为 `admin`、`employee1`、`employee2`，在门店无人操作时由服务器 root 交互执行 `python3 /root/laundry-rotate-users.py`。脚本先验证近 36 小时的备份，再分别提示输入三个新登录名和不回显的密码（每个密码输入两次），最后必须输入 `ROTATE` 才会事务更新。不要在聊天、命令参数或工单中发送明文密码。轮换后所有员工重新登录；用新账号逐一验证，再确认旧登录名不可用。若账号清单与预期不同，脚本会拒绝修改。`--check` 仅做只读检查，`--self-test` 只操作连接内临时表。

在服务器上将新版后端 JAR 暂存到随机命名的 `/tmp/laundry-preflight.*/backend.jar` 后，以 root 执行 `ops/rehearse-restored-backup.sh /var/backups/laundry-mysql/laundry_db-时间.sql.gz /tmp/laundry-preflight.随机值/backend.jar`。脚本只接受指定备份目录与临时 JAR 路径，先校验 gzip；恢复到随机临时库，创建仅有该库权限的短期账号，让新版后端在无 Web、无消息消费、无正式照片目录的环境中执行 Flyway，再比较订单、衣物、顾客数量和实收合计。成功时输出 `RESTORED_MIGRATION_OK`，退出时删除临时库与账号；务必再核对正式库迁移版本未变、临时库和账号均不存在。不要把真实备份下载到开发电脑，也不要在正式库回填订单时间做隐私功能测试。

## RabbitMQ

本机开发使用管理版容器，生产环境使用独立 RabbitMQ 实例或云消息队列，不与应用共享数据盘。必须配置：

```text
RABBITMQ_HOST=RabbitMQ地址
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=独立应用账号
RABBITMQ_PASSWORD=强密码
RABBITMQ_LISTENER_ENABLED=true
```

健康检查：

```powershell
docker exec laundry-rabbitmq rabbitmq-diagnostics -q check_running
docker exec laundry-rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged
```

订单事务只写 `mq_outbox`，后台收到 Broker Confirm 后才标记 `SENT`。失败自动指数退避，最多 8 次；消费失败重试 3 次后进入死信并记录为 `DEAD`。管理员可在“远程维护 → 消息任务”查看失败原因并重新投递。不要直接修改或删除 `mq_outbox`、`mq_consumed_event`。

## Windows 备份

```powershell
$env:DB_PASSWORD = '实际密码'
Set-Location C:\path\to\laundry-backend
.\ops\backup-mysql.ps1 -BackupDirectory 'D:\LaundryBackups'
```

用独立测试库做恢复演练，不能直接覆盖生产库：

```powershell
$env:DB_PASSWORD = '实际密码'
.\ops\restore-mysql.ps1 -BackupFile 'D:\LaundryBackups\laundry_db-时间.sql' -Database 'laundry_db_restore_test'
```

## Linux 备份

```bash
export DB_PASSWORD='实际密码'
export BACKUP_DIR=/srv/backups/laundry
./ops/backup-mysql.sh
```

恢复演练：

```bash
export DB_PASSWORD='实际密码'
./ops/restore-mysql.sh /srv/backups/laundry/laundry_db-时间.sql laundry_db_restore_test
```

## 推荐计划

- 每天凌晨一次全量备份，业务高峰期每小时使用云数据库时间点恢复或增量备份。
- 本机保留 7 天，异地对象存储保留 30 天，每月长期保留一份。
- 上传异地前启用服务端加密，备份账号只有写入指定目录的权限。
- 每月恢复到独立测试库，并核对核心表数量、订单数量及最近一笔订单。
- 备份成功、校验失败、连续两次未产生备份都要告警。
