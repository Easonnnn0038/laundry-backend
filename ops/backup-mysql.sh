#!/usr/bin/env bash
set -euo pipefail

: "${DB_PASSWORD:?请先设置 DB_PASSWORD}"
DB_NAME="${DB_NAME:-laundry_db}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-root}"
BACKUP_DIR="${BACKUP_DIR:-$(cd "$(dirname "$0")/.." && pwd)/backups}"
mkdir -p "$BACKUP_DIR"
BACKUP_FILE="$BACKUP_DIR/$DB_NAME-$(date +%Y%m%d-%H%M%S).sql"

MYSQL_PWD="$DB_PASSWORD" mysqldump --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" \
  --single-transaction --routines --triggers --events --default-character-set=utf8mb4 \
  --result-file="$BACKUP_FILE" "$DB_NAME"
sha256sum "$BACKUP_FILE" > "$BACKUP_FILE.sha256"
chmod 600 "$BACKUP_FILE" "$BACKUP_FILE.sha256"
echo "备份完成: $BACKUP_FILE"
