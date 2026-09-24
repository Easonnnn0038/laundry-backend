# 生产部署与备份

## 启动前

生产环境设置 `SPRING_PROFILES_ACTIVE=prod`，再按 `production.env.example` 提供所有变量。不要把真实 `.env`、JWT 密钥、数据库密码、RabbitMQ 密码或设备令牌提交到 Git。

门店后端是数据库迁移的唯一执行者。现有数据库首次启动会基线为 V7 并执行 V8；工厂后端不得单独维护 Flyway 版本。

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
