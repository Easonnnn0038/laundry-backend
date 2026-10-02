# 小木棒洗衣 · 门店后端

门店业务与微信小程序共用的后端服务，负责收衣、会员、送厂、回店上架、取衣、返洗、通知任务和运营维护。

## 主要功能

- 门店员工 JWT 登录与角色权限控制
- 收衣开单、会员卡、充值、条码及瑕疵照片
- 装车送厂、逐件扫码回店、自动分配货架
- 手机号或四位取衣码查询，支持部分取件
- 客返/店返、取衣通知、营业与收入统计
- 微信小程序登录、手机号绑定及本人订单查询
- RabbitMQ Outbox、消费幂等、失败重试与死信记录
- Flyway 数据库迁移、诊断日志及备份/恢复脚本

## 技术栈

Java 17、Spring Boot 3.2.5、Spring Security、MyBatis-Plus、MySQL 8、Flyway、RabbitMQ、JWT、Knife4j、ZXing。

## 本地运行

要求：JDK 17、Maven 3.8+、MySQL 8、RabbitMQ。

```powershell
$env:DB_PASSWORD = '你的数据库密码'
mvn spring-boot:run
```

默认端口为 `8080`。开发环境接口文档：<http://localhost:8080/doc.html>。

数据库结构由 Flyway 自动维护。已有生产库以 V7 为基线继续迁移，不要手工重放历史 SQL。

## 常用配置

| 环境变量 | 用途 |
| --- | --- |
| `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | MySQL 连接 |
| `JWT_SECRET` | JWT 签名密钥 |
| `RABBITMQ_HOST`、`RABBITMQ_PORT` | RabbitMQ 地址 |
| `RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD` | RabbitMQ 凭证 |
| `PHOTO_UPLOAD_DIR` | 照片存储目录 |
| `CORS_ALLOWED_ORIGINS` | 允许的前端来源 |
| `WECHAT_APP_ID`、`WECHAT_APP_SECRET` | 微信小程序凭证 |

生产环境使用 `prod` Profile，完整示例见 [`ops/production.env.example`](ops/production.env.example)，部署与备份说明见 [`ops/DEPLOYMENT.md`](ops/DEPLOYMENT.md)。

## 验证与构建

```bash
mvn test
mvn clean package
```

## 安全说明

- 不要提交数据库密码、JWT 密钥、微信密钥、设备令牌、照片或数据库备份。
- 生产环境不会自动创建或重置默认账号；初始化账号应通过受控流程完成。
- 小程序令牌只能访问 `/api/miniapp/**`，不能调用门店员工接口。

## 相关仓库

- [门店前端](https://github.com/Easonnnn0038/lanudry-frontend)
- [工厂后端](https://github.com/Easonnnn0038/laundryfactory_b)
- [工厂前端](https://github.com/Easonnnn0038/laundryfactory_f)
