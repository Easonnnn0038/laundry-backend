#!/usr/bin/env bash
set -euo pipefail

release=/opt/laundry/releases/miniapp-candidate-20261015-8b987880
base=/opt/laundry/isolated
db=laundry_test_20261015
vhost=/laundry_test_20261015
account=laundry_test_20261015

test ! -e "$base"
test ! -e /etc/laundry/isolated.env
test "$(mysql -N -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$db'")" = 0
test "$(mysql -N -e "SELECT COUNT(*) FROM mysql.user WHERE user='$account'")" = 0
test "$(docker exec laundry-rabbitmq rabbitmqctl list_vhosts -q | grep -cx "$vhost" || true)" = 0
test "$(docker exec laundry-rabbitmq rabbitmqctl list_users -q | grep -c "^$account[[:space:]]" || true)" = 0
sha256sum -c <<'HASHES'
5679dedda705eecd8a0ea5fc68787778a58acbc3beba2cea07a1f4176065ccca  /opt/laundry/releases/miniapp-candidate-20261015-8b987880/laundry-backend.jar
19755df10ec76659a8eeceae2c2a040488391fc060dd0457e2bc3854ed4c3330  /opt/laundry/releases/miniapp-candidate-20261015-8b987880/laundry-factory.jar
7fcb178d1596b8334ba53666af522b7c05347a7caa04c37b1b43d0ccdb766a8e  /opt/laundry/releases/miniapp-candidate-20261015-8b987880/web-store.zip
HASHES

install -d -m 755 "$base" "$base/web-store" "$base/web-factory" "$base/uploads" "$base/uploads/photos" "$base/uploads/quality"
install -m 644 "$release/laundry-backend.jar" "$base/laundry-backend.jar"
install -m 644 "$release/laundry-factory.jar" "$base/laundry-factory.jar"
unzip -q "$release/web-store.zip" -d "$base/web-store"
cp -a /opt/laundry/web-factory/. "$base/web-factory/"
chown -R ubuntu:ubuntu "$base"

mysql -e "CREATE DATABASE $db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
mysqldump --no-data --skip-triggers --single-transaction laundry_db | mysql "$db"
for table in store clothes_category member_card_type factory_sort_type factory_system_config schema_installation_guard flyway_schema_history shelf_config; do
    mysql -e "INSERT INTO $db.$table SELECT * FROM laundry_db.$table"
done

db_password=$(openssl rand -hex 24)
rabbit_password=$(openssl rand -hex 24)
jwt_secret=$(openssl rand -hex 32)
test_key=$(openssl rand -hex 32)
device_token=$(openssl rand -hex 24)
mysql -e "CREATE USER '$account'@'localhost' IDENTIFIED BY '$db_password'; GRANT ALL PRIVILEGES ON $db.* TO '$account'@'localhost'"
docker exec laundry-rabbitmq rabbitmqctl add_vhost "$vhost"
docker exec laundry-rabbitmq rabbitmqctl add_user "$account" "$rabbit_password"
docker exec laundry-rabbitmq rabbitmqctl set_permissions -p "$vhost" "$account" '.*' '.*' '.*'

db_url="jdbc:mysql://127.0.0.1:3306/$db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
umask 027
{
    printf 'DB_URL=%s\nSPRING_DATASOURCE_URL=%s\n' "$db_url" "$db_url"
    printf 'DB_USERNAME=%s\nSPRING_DATASOURCE_USERNAME=%s\nDB_PASSWORD=%s\nSPRING_DATASOURCE_PASSWORD=%s\n' "$account" "$account" "$db_password" "$db_password"
    printf 'RABBITMQ_HOST=127.0.0.1\nRABBITMQ_PORT=5672\nRABBITMQ_USERNAME=%s\nRABBITMQ_PASSWORD=%s\nSPRING_RABBITMQ_VIRTUAL_HOST=%s\nRABBITMQ_LISTENER_ENABLED=true\n' "$account" "$rabbit_password" "$vhost"
    printf 'JWT_SECRET=%s\nMINIAPP_TEST_LOGIN_ENABLED=true\nMINIAPP_TEST_LOGIN_KEY=%s\nMINIAPP_TEST_PHONE=13900000000\n' "$jwt_secret" "$test_key"
    printf 'APP_SEED_DEFAULT_USERS=true\nPHOTO_UPLOAD_DIR=%s/uploads/photos\nFACTORY_QUALITY_PHOTO_DIR=%s/uploads/quality\n' "$base" "$base"
    printf 'FACTORY_DEVICE_TOKENS=test-device:%s\nFACTORY_HOST=127.0.0.1\nFACTORY_PORT=18081\n' "$device_token"
    printf 'CORS_ALLOWED_ORIGINS=http://127.0.0.1:18082,http://localhost:18082\nFACTORY_ALLOWED_ORIGINS=http://127.0.0.1:18082,http://localhost:18082\n'
    printf 'KNIFE4J_ENABLE=false\nKNIFE4J_ENABLED=false\nSPRINGDOC_API_DOCS_ENABLED=false\nSPRINGDOC_SWAGGER_UI_ENABLED=false\n'
} > /etc/laundry/isolated.env
chown root:ubuntu /etc/laundry/isolated.env
chmod 640 /etc/laundry/isolated.env

echo 'ISOLATED_PREPARED'
