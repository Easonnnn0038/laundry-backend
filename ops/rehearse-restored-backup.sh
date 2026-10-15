#!/usr/bin/env bash
# Run on the laundry server as root. Never migrates laundry_db.
set -euo pipefail
umask 077

backup=${1:?Usage: rehearse-restored-backup.sh BACKUP_FILE NEW_BACKEND_JAR}
jar=${2:?Usage: rehearse-restored-backup.sh BACKUP_FILE NEW_BACKEND_JAR}
case "$backup" in /var/backups/laundry-mysql/laundry_db-*.sql.gz) ;; *) echo 'Unexpected backup path' >&2; exit 2 ;; esac
case "$jar" in /tmp/laundry-preflight.*/backend.jar) ;; *) echo 'Unexpected jar path' >&2; exit 2 ;; esac
test -f "$backup" && test -f "$jar"
gzip -t "$backup"

database="laundry_rehearsal_$(date -u +%Y%m%d%H%M%S)_$(openssl rand -hex 3)"
db_user="lr_$(openssl rand -hex 4)"
db_password=$(openssl rand -hex 24)
workdir=$(mktemp -d /tmp/laundry-rehearsal.XXXXXXXX)
created=0
java_pid=''

cleanup() {
  local result=$?
  trap - EXIT
  if [[ -n "$java_pid" ]] && kill -0 "$java_pid" 2>/dev/null; then
    kill "$java_pid" 2>/dev/null || true
    wait "$java_pid" 2>/dev/null || true
  fi
  if [[ "$created" == 1 ]]; then
    if ! mysql -e "DROP DATABASE IF EXISTS \`$database\`; DROP USER IF EXISTS '$db_user'@'localhost';"; then
      echo "Temporary database or account cleanup failed: $database / $db_user" >&2
      result=1
    fi
  fi
  if [[ "$result" == 0 ]]; then rm -r -- "$workdir"; else echo "Restricted failure log: $workdir/app.log" >&2; fi
  exit "$result"
}
trap cleanup EXIT

exists=$(mysql --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$database';")
test "$exists" = 0 || { echo 'Temporary database already exists' >&2; exit 1; }
mysql -e "CREATE DATABASE \`$database\` CHARACTER SET utf8mb4;"
created=1
mysql -e "CREATE USER '$db_user'@'localhost' IDENTIFIED BY '$db_password'; GRANT ALL PRIVILEGES ON \`$database\`.* TO '$db_user'@'localhost';"
MYSQL_PWD="$db_password" mysql --protocol=TCP --host=127.0.0.1 --user="$db_user" "$database" -e 'SELECT 1' >/dev/null
gzip -cd "$backup" | mysql "$database"

before=$(mysql --batch --skip-column-names "$database" -e "SELECT CONCAT((SELECT COUNT(*) FROM laundry_order),'|',(SELECT COUNT(*) FROM order_item),'|',(SELECT COUNT(*) FROM customer),'|',(SELECT COUNT(*) FROM pickup_order),'|',(SELECT COUNT(*) FROM member_card),'|',(SELECT COALESCE(SUM(total_paid),0) FROM laundry_order),'|',(SELECT COALESCE(SUM(balance),0) FROM member_card));")
version=$(mysql --batch --skip-column-names "$database" -e "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1;")
case "$version" in 19|20|21|22|23|24|25|26) ;; *) echo "Unexpected backup migration version: $version" >&2; exit 1 ;; esac

# The account has privileges only on this random database, so even a bad URL cannot migrate production.
set -a
. /etc/laundry/store.env
set +a
export DB_URL="jdbc:mysql://127.0.0.1:3306/$database?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
export DB_USERNAME="$db_user" DB_PASSWORD="$db_password"
export RABBITMQ_HOST=127.0.0.1 RABBITMQ_PORT=1
export PHOTO_UPLOAD_DIR="$workdir/photos"

java -jar "$jar" --spring.main.web-application-type=none \
  --spring.rabbitmq.listener.simple.auto-startup=false \
  --app.rabbitmq.outbox-poll-ms=86400000 --app.seed-default-users=false \
  --photo.cleanup-cron='0 0 0 1 1 *' >"$workdir/app.log" 2>&1 &
java_pid=$!

ready=0
for _ in {1..90}; do
  migrated=$(mysql --batch --skip-column-names "$database" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version='27' AND success=1;" 2>/dev/null || true)
  if [[ "$migrated" == 1 ]]; then ready=1; break; fi
  if ! kill -0 "$java_pid" 2>/dev/null; then break; fi
  sleep 1
done
if [[ "$ready" != 1 ]]; then
  echo 'Migration did not reach V27' >&2
  exit 1
fi
kill "$java_pid" 2>/dev/null || true
wait "$java_pid" 2>/dev/null || true
java_pid=''

after=$(mysql --batch --skip-column-names "$database" -e "SELECT CONCAT((SELECT COUNT(*) FROM laundry_order),'|',(SELECT COUNT(*) FROM order_item),'|',(SELECT COUNT(*) FROM customer),'|',(SELECT COUNT(*) FROM pickup_order),'|',(SELECT COUNT(*) FROM member_card),'|',(SELECT COALESCE(SUM(total_paid),0) FROM laundry_order),'|',(SELECT COALESCE(SUM(balance),0) FROM member_card));")
test "$before" = "$after" || { echo 'Order/customer/member counts or money changed' >&2; exit 1; }
test "$(mysql --batch --skip-column-names "$database" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('23','24','25','26','27') AND success=1;")" = 5
test "$(mysql --batch --skip-column-names "$database" -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$database' AND TABLE_NAME IN ('miniapp_deletion_request','miniapp_deletion_cleanup_audit','delivery_fee_rule','home_delivery_payment','customer_contact_log');")" = 5
echo "RESTORED_MIGRATION_OK from_v=$version to_v=27 snapshot=$before"
