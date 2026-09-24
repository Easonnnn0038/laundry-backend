#!/usr/bin/env bash
set -euo pipefail

: "${DB_PASSWORD:?请先设置 DB_PASSWORD}"
: "${1:?用法: restore-mysql.sh BACKUP_FILE [DATABASE]}"
BACKUP_FILE="$(realpath "$1")"
DB_NAME="${2:-laundry_db_restore_test}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-root}"
[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || { echo '数据库名不合法' >&2; exit 1; }

MYSQL_PWD="$DB_PASSWORD" mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" \
  --execute="CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
MYSQL_PWD="$DB_PASSWORD" mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" "$DB_NAME" < "$BACKUP_FILE"
echo "恢复完成: $BACKUP_FILE -> $DB_NAME"
