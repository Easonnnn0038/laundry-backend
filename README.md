# 小木棒洗衣门店管理系统 - 后端服务

基于 Spring Boot 3 的洗衣门店管理系统后端，提供客户管理、会员卡、收衣订单、衣物跟踪、数据统计等完整 API 服务。

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 语言版本 |
| Spring Boot | 3.2.5 | 核心框架 |
| Spring Security | 6.x | 认证与授权 |
| MyBatis-Plus | 3.5.5 | ORM 框架 |
| MySQL | 8.0+ | 关系型数据库 |
| JWT (jjwt) | 0.12.5 | Token 认证 |
| Knife4j | 4.4.0 | OpenAPI3 接口文档 |
| ZXing | 3.5.3 | 条码生成（Code128） |
| Lombok | - | 简化 Java Bean |

## 项目结构

```
laundry-backend/
├── sql/
│   ├── init.sql                  # 初始化脚本（必须先执行）
│   └── receive_clothes.sql       # 业务表结构脚本
├── src/main/java/com/laundry/api/
│   ├── common/                   # 通用类（统一响应、全局异常处理）
│   ├── config/                   # 配置类（安全、跨域、MP、文档等）
│   ├── controller/               # 控制器层
│   │   ├── AuthController        # 登录认证
│   │   ├── CategoryController    # 衣物类别
│   │   ├── CustomerController    # 客户管理
│   │   ├── MemberCardController  # 会员卡
│   │   ├── OrderController       # 收衣订单 / 暂存 / 送厂 / 回店
│   │   ├── PhotoController       # 瑕疵照片上传
│   │   └── StoreController       # 门店信息
│   ├── dto/                      # 请求/响应 DTO
│   ├── entity/                   # 数据库实体
│   ├── mapper/                   # MyBatis-Plus Mapper
│   ├── security/                 # JWT 过滤器、工具类、UserDetails
│   ├── service/                  # 业务层
│   │   └── impl/                 # 业务实现
│   └── utils/                    # 工具类（条码、密码、当前用户）
└── src/main/resources/
    ├── application.yml           # 主配置文件
    └── mapper/                   # 自定义 XML Mapper
```

## 环境要求

- **JDK 17** 及以上
- **Maven 3.8+**
- **MySQL 8.0+**（MariaDB 不可用）

## 快速开始

### 1. 数据库初始化

```sql
-- 先执行基础数据脚本（用户、门店、衣物类别、会员卡类型）
source sql/init.sql;

-- 再执行业务表脚本
source sql/receive_clothes.sql;
```

### 2. 修改配置文件

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/laundry_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: 你的MySQL密码   # 修改这里
```

### 3. 启动服务

```bash
# 方式一：Maven 命令
mvn spring-boot:run

# 方式二：先打包再运行
mvn clean package -DskipTests
java -jar target/laundry-backend-1.0.0.jar

# 方式三：IDE 中直接运行 LaundryApiApplication.java
```

服务启动后访问：

| 资源 | 地址 |
|------|------|
| 服务端口 | http://localhost:8080 |
| 接口文档 (Knife4j) | http://localhost:8080/doc.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

## 默认账号

执行 `init.sql` 后自动创建以下用户（密码均为 `admin123` / `emp123`）：

| 用户名 | 密码 | 角色 | 姓名 | 门店 |
|--------|------|------|------|------|
| admin | admin123 | ADMIN 管理员 | 王朋 | 001 |
| employee1 | emp123 | EMPLOYEE 员工 | 邵恒剑 | 001 |
| employee2 | emp123 | EMPLOYEE 员工 | 刘洪刚 | 001 |

## 核心业务规则

### 会员卡
- 卡号生成规则：`MC` + `yyyyMMdd` + `3位流水号`，如 `MC20260812001`
- 办卡 300/500 元直接充值到卡余额
- 折扣：8.8折卡（300元）/ 6.8折卡（500元）
- 单人单卡（一人只能持有一张有效卡）

### 收衣订单
- 订单条码：门店号(3位) + MMDD(4位) + 订单序号(3位) + 衣物序号(2位)
- 支付优先级：会员卡余额 > 补差方式（现金/微信/支付宝）
- 卡扣余额不足时自动补差，需选择补差方式
- 加急订单：在折扣后金额基础上加收 20% 加急费

### 办卡/充值流程
- 新客户收衣同时办卡：办卡费计入应收账单
- 老客户自动转为充值：办卡金额直接追加到卡余额
- 充值金额计入收衣订单实收，与洗衣费合并计算

### 订单状态流转
```
RECEIVED（待送厂）
  → SENT_TO_FACTORY（运输中，送厂批量操作记录工厂批次）
    → BACK_TO_STORE（已回店，上架录入货架号）
      → NOTIFIED（已通知取衣）
        → PICKED_UP（已取衣，完成）
```

## 主要 API 概览

所有 API 统一前缀 `/api`，登录后需携带 JWT Header：
```
Authorization: Bearer <token>
```

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 认证 | POST | `/api/auth/login` | 登录获取 Token |
| 客户 | GET | `/api/customer/search?phone=xxx` | 手机号查客户 |
| 会员卡 | POST | `/api/member-card` | 独立办卡 |
| 会员卡 | POST | `/api/member-card/recharge` | 卡充值 |
| 收衣 | POST | `/api/order/receive` | 提交收衣订单 |
| 暂存 | GET | `/api/order/staging` | 暂存订单列表 |
| 暂存 | GET | `/api/order/staging/{id}` | 暂存订单详情 |
| 送厂 | POST | `/api/order/send-to-factory` | 批量送厂 |
| 回店 | POST | `/api/order/back-to-store` | 回店录入货架号 |
| 取衣 | POST | `/api/order/pickup/{id}` | 完成取衣 |
| 首页 | GET | `/api/order/dashboard/stats` | 首页统计数据 |
| 首页 | GET | `/api/order/dashboard/recent` | 最近订单 |

详细接口文档启动后访问 **http://localhost:8080/doc.html**。

## 照片存储

- 上传目录（可配置）：`./uploads/photos`
- 访问路径：`http://localhost:8080/photos/**`
- 订单级瑕疵照片以 JSON 数组形式存入 `laundry_order.defect_photos` 字段

## 开发提示

### 取衣通知、查询与统计增量表

现有数据库在使用这些页面前需执行一次 `sql/V4__store_operations.sql`（不要重跑 `receive_clothes.sql`）。该脚本只新增 `pickup_notification` 和 `store_return_error` 表。

- 取衣通知为员工电话或微信联系后的手动登记，不会自动发送短信。通知含四位取衣码，订单须完整回店才可登记。
- 衣物查询只返回当前门店数据，接口不返回顾客姓名。
- 错误回店和营业/收入统计接口在后端校验 `ADMIN` 角色。异常大件重新核对时清除旧扫描记录，需重新逐件签收；错店大件不能在当前门店签收。
- 营业统计按取衣闭单日确认洗衣服务金额；收入统计按收衣实收日计算顾客付款。会员卡扣款不重复计入实收。取消订单按 `total_paid` 全额列为**应退估算**，现阶段没有实际退款流水或退款执行接口。

### 补收附件与取消申请

已有数据库还需执行一次 `sql/V5__supplement_and_cancellation.sql`。本机开发库已执行。

- 补收附件关联原衣物，生成独立衣物码。原单未送厂时附件并入原单；原单已送厂时生成独立大件，可从补收页面创建新的送厂批次。
- 补收金额当前只记录为 `PENDING` 待收，不修改订单实收或会员卡余额，等待支付模块接入。
- “删除查询”采用取消申请：店员提交、管理员批准或驳回。只有未送厂订单可申请和批准，订单数据不会物理删除。
- 管理员可恢复已批准的误取消订单；已有物流记录的订单不能恢复。退款与支付冲正暂未实现。

### 远程维护诊断中心

已有数据库需执行一次 `sql/V6__maintenance_center.sql`；本机开发库已执行。该模块只提供诊断，不包含远程桌面、命令执行、服务重启或数据库修改能力。

- 管理员可查看门店后端、MySQL、工厂后端、RabbitMQ 和 Redis 的连接状态。
- 全局异常处理会自动记录系统错误、参数及业务异常；同一错误在 24 小时内重复发生时合并计数。
- 门店前端会尽力上报接口失败；后端不可达时上报也会失败，不影响原页面操作。
- 日志可标记为待处理、处理中、已解决或已忽略，并填写处理说明；支持 CSV 导出。
- 操作记录复用 `order_operate_log`。诊断日志不保存请求体，并过滤密码、令牌、密钥和 Authorization 等敏感内容。

- 编译项目：`mvn clean compile`
- 只运行测试：`mvn test`
- 跳过测试打包：`mvn clean package -DskipTests`
- MyBatis-Plus 实体字段驼峰自动映射为数据库下划线（数字前无下划线，如 `memberPrice300` → `member_price300`）

## 相关仓库

- 前端项目：[laundry-frontend](https://github.com/Easonnnn0038/lanundry-frontend)
