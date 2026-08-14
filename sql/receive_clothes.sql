-- ============================================================
-- 小木棒洗衣门店管理系统 - 收衣功能数据库脚本
-- 包含：重建衣物类别表、收衣核心表、业务辅助表
-- 执行顺序：先 DROP 旧表，再 CREATE 新表，最后 INSERT 数据
-- ============================================================

USE `laundry_db`;

-- ============================================================
-- 0. 重建衣物类别表 clothes_category
--    按门店价目表重新设计：6大板块共66项
--    衣物类22 + 皮衣奢饰品13 + 家纺13 + 鞋类7 + 单烫6 + 包包5 = 66
-- ============================================================
DROP TABLE IF EXISTS `clothes_category`;
CREATE TABLE `clothes_category` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `category_group`  VARCHAR(30)   NOT NULL COMMENT '板块分组：CLOTHES衣物类/LEATHER皮衣奢饰品护理/HOME家纺卧室/SHOES鞋类/IRON单烫类/BAG包包类',
    `category_level1` VARCHAR(50)   DEFAULT NULL COMMENT '一级分类（与板块对应）',
    `category_level2` VARCHAR(100)  NOT NULL COMMENT '二级分类（具体项目名称，如西装、运动鞋）',
    `category_level3` VARCHAR(50)   DEFAULT NULL COMMENT '三级分类（预留）',
    `price`           DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '原价（非会员价，价目表起步价）',
    `unit`            VARCHAR(10)   DEFAULT '件' COMMENT '计价单位：件/条/个/套/双/平方/对',
    `price_mode`      TINYINT       NOT NULL DEFAULT 1 COMMENT '会员价模式：1=按比例折扣 2=固定会员价',
    `member_price_300` DECIMAL(10,2) DEFAULT NULL COMMENT '300元卡会员价（固定价模式用，折扣模式此字段为NULL）',
    `member_price_500` DECIMAL(10,2) DEFAULT NULL COMMENT '500元卡会员价（固定价模式用，折扣模式此字段为NULL）',
    `remark`          VARCHAR(255)  DEFAULT NULL COMMENT '备注（如"起"表示起步价，价格可手动调整）',
    `sort_order`      INT           NOT NULL DEFAULT 0 COMMENT '排序号（同板块内的显示顺序）',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_group` (`category_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='衣物类别表（按门店价目表6大板块重建）';

-- --- 板块1：衣物类 CLOTHES 共22种（价格模式=1按折扣：300卡8.8折/500卡6.8折）---
INSERT INTO `clothes_category` (`category_group`,`category_level1`,`category_level2`,`price`,`unit`,`price_mode`,`remark`,`sort_order`) VALUES
('CLOTHES','衣物类','西装',       30.00,'件',1,'起',1),
('CLOTHES','衣物类','西裤/牛仔裤/领带',20.00,'件',1,'起',2),
('CLOTHES','衣物类','衬衫T恤',    20.00,'件',1,'起',3),
('CLOTHES','衣物类','真丝衬衫',   30.00,'件',1,'起',4),
('CLOTHES','衣物类','羊绒大衣',   40.00,'件',1,'起',5),
('CLOTHES','衣物类','PU革上衣',   40.00,'件',1,'起',6),
('CLOTHES','衣物类','羽绒马甲',   20.00,'件',1,'起',7),
('CLOTHES','衣物类','内胆',       30.00,'件',1,'起',8),
('CLOTHES','衣物类','牛仔外套',   30.00,'件',1,'起',9),
('CLOTHES','衣物类','羽绒服',     30.00,'件',1,'起',10),
('CLOTHES','衣物类','拼PU羽绒服', 40.00,'件',1,'起',11),
('CLOTHES','衣物类','冲锋衣',     30.00,'件',1,'起',12),
('CLOTHES','衣物类','儿童羽绒服', 20.00,'件',1,'起',13),
('CLOTHES','衣物类','羊毛衫',     30.00,'件',1,'起',14),
('CLOTHES','衣物类','羊毛裤',     25.00,'件',1,'起',15),
('CLOTHES','衣物类','裙子/连衣裙',40.00,'件',1,'起',16),
('CLOTHES','衣物类','礼服/婚纱',  100.00,'件',1,'起',17),
('CLOTHES','衣物类','风衣',       45.00,'件',1,'起',18),
('CLOTHES','衣物类','旗袍',       50.00,'件',1,'起',19),
('CLOTHES','衣物类','围巾',       30.00,'件',1,'起',20),
('CLOTHES','衣物类','毛领',       15.00,'件',1,'起',21),
('CLOTHES','衣物类','其他上衣',   30.00,'件',1,'起',22);

-- --- 板块2：皮衣/奢饰品护理 LEATHER 共13种（价格模式=1按折扣）---
INSERT INTO `clothes_category` (`category_group`,`category_level1`,`category_level2`,`price`,`unit`,`price_mode`,`remark`,`sort_order`) VALUES
('LEATHER','皮衣/奢饰品护理','皮衣清洗',        100.00,'件',1,'起',1),
('LEATHER','皮衣/奢饰品护理','皮裙皮裤',         40.00,'件',1,'起',2),
('LEATHER','皮衣/奢饰品护理','绒面皮衣清洗',    150.00,'件',1,'起',3),
('LEATHER','皮衣/奢饰品护理','皮毛手套清洗',     45.00,'件',1,'起',4),
('LEATHER','皮衣/奢饰品护理','皮衣上色黑',      220.00,'件',1,'起',5),
('LEATHER','皮衣/奢饰品护理','皮衣上色彩',      300.00,'件',1,'起',6),
('LEATHER','皮衣/奢饰品护理','皮鞋上色黑',      100.00,'件',1,'起',7),
('LEATHER','皮衣/奢饰品护理','皮鞋上色彩',      160.00,'件',1,'起',8),
('LEATHER','皮衣/奢饰品护理','绒面皮衣上色',    380.00,'件',1,'起',9),
('LEATHER','皮衣/奢饰品护理','皮裙皮裤上色黑',   80.00,'件',1,'起',10),
('LEATHER','皮衣/奢饰品护理','水貂毛/狐狸毛/兔毛',300.00,'件',1,'起',11),
('LEATHER','皮衣/奢饰品护理','皮毛一体',        300.00,'件',1,'起',12),
('LEATHER','皮衣/奢饰品护理','鞋边去黄氧化',     80.00,'件',1,'起',13);

-- --- 板块3：卧室/家用家纺 HOME 共13种（价格模式=1按折扣）---
INSERT INTO `clothes_category` (`category_group`,`category_level1`,`category_level2`,`price`,`unit`,`price_mode`,`remark`,`sort_order`) VALUES
('HOME','家纺卧室','四件套',       80.00,'套',1,'起',1),
('HOME','家纺卧室','三件套',       70.00,'套',1,'起',2),
('HOME','家纺卧室','枕套',         10.00,'个',1,'起',3),
('HOME','家纺卧室','床单',         30.00,'条',1,'起',4),
('HOME','家纺卧室','普通被子',     30.00,'条',1,'起',5),
('HOME','家纺卧室','羽绒被',       40.00,'条',1,'起',6),
('HOME','家纺卧室','羊毛/蚕丝被/驼绒被',80.00,'条',1,'起',7),
('HOME','家纺卧室','毛巾/浴巾',    10.00,'条',1,'起',8),
('HOME','家纺卧室','地毯',         50.00,'平方',1,'按平方计价',9),
('HOME','家纺卧室','毛绒玩具',     40.00,'个',1,'起',10),
('HOME','家纺卧室','窗帘',         40.00,'对',1,'40/对',11),
('HOME','家纺卧室','窗纱',         20.00,'对',1,'20/对',12),
('HOME','家纺卧室','沙发套',       30.00,'套',1,'大40小20，先填30可手动改',13);

-- --- 板块4：鞋类 SHOES 共7种（价格模式=1按折扣：300卡8.8折/500卡6.8折）---
INSERT INTO `clothes_category` (`category_group`,`category_level1`,`category_level2`,`price`,`unit`,`price_mode`,`remark`,`sort_order`) VALUES
('SHOES','鞋类','运动鞋',         30.00,'双',1,'起',1),
('SHOES','鞋类','绒面/高帮绒面鞋', 45.00,'双',1,'起',2),
('SHOES','鞋类','奢饰鞋',         60.00,'双',1,'起',3),
('SHOES','鞋类','雪地靴',         60.00,'双',1,'起',4),
('SHOES','鞋类','高帮雪地靴',     80.00,'双',1,'起',5),
('SHOES','鞋类','皮鞋',           40.00,'双',1,'起',6),
('SHOES','鞋类','马丁靴',         60.00,'双',1,'起',7);

-- --- 板块5：单烫类 IRON 共6种（价格模式=2固定价：300卡8元/500卡5元）---
INSERT INTO `clothes_category` (`category_group`,`category_level1`,`category_level2`,`price`,`unit`,`price_mode`,`member_price_300`,`member_price_500`,`remark`,`sort_order`) VALUES
('IRON','单烫类','衬衫',  10.00,'件',2, 8.00, 5.00, '起', 1),
('IRON','单烫类','裤子',  10.00,'件',2, 8.00, 5.00, '起', 2),
('IRON','单烫类','西装',  15.00,'件',2, 8.00, 5.00, '起', 3),
('IRON','单烫类','大衣',  20.00,'件',2, 8.00, 5.00, '起', 4),
('IRON','单烫类','围巾',   5.00,'件',2, 8.00, 5.00, '起', 5),
('IRON','单烫类','裙子',  15.00,'件',2, 8.00, 5.00, '',   6);

-- --- 板块6：包包类清洗护理 BAG 共5种（价格模式=1按折扣）---
INSERT INTO `clothes_category` (`category_group`,`category_level1`,`category_level2`,`price`,`unit`,`price_mode`,`remark`,`sort_order`) VALUES
('BAG','包包类','双肩背包',      35.00,'个',1,'起',1),
('BAG','包包类','手提包/皮包',   40.00,'个',1,'起',2),
('BAG','包包类','电脑包',        40.00,'个',1,'',  3),
('BAG','包包类','奢饰皮包',     150.00,'个',1,'起',4),
('BAG','包包类','奢饰皮包护理', 180.00,'个',1,'起',5);

-- ============================================================
-- 验证衣物类别总数：22 + 13 + 13 + 7 + 6 + 5 = 66 条
-- ============================================================


-- ============================================================
-- 0b. 会员卡类型表 member_card_type（必需的基础数据）
--     每人一卡，办卡/充值类型都来自本表
-- ============================================================
DROP TABLE IF EXISTS `member_card_type`;
CREATE TABLE `member_card_type` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`          VARCHAR(50)   NOT NULL COMMENT '卡类型名称（如 8.8折卡、6.8折卡）',
    `amount`        DECIMAL(10,2) NOT NULL COMMENT '办卡/充值金额（入账余额）',
    `discount_rate` DECIMAL(5,2)  NOT NULL COMMENT '折扣率：price_mode=1（按折扣）类别使用，如8.80=8.8折',
    `status`        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `remark`        VARCHAR(255)  DEFAULT NULL COMMENT '说明',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员卡类型配置表';

-- 默认卡类型（按需求文档：充300=8.8折卡，充500=6.8折卡）
INSERT INTO `member_card_type` (`id`, `name`, `amount`, `discount_rate`, `status`, `remark`) VALUES
(1, '8.8折卡', 300.00, 8.80, 1, '收衣同时办卡或单独办卡，办卡金额入余额，price_mode=1的类别按8.8折，单烫类8元/件'),
(2, '6.8折卡', 500.00, 6.80, 1, '收衣同时办卡或单独办卡，办卡金额入余额，price_mode=1的类别按6.8折，单烫类5元/件');


-- ============================================================
-- 1. 客户表 customer
-- ============================================================
DROP TABLE IF EXISTS `customer`;
CREATE TABLE `customer` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`        VARCHAR(50)  NOT NULL COMMENT '客户姓名（必填）',
    `phone`       VARCHAR(20)  NOT NULL COMMENT '联系电话（必填，用于自动带出客户信息）',
    `address`     VARCHAR(255) DEFAULT NULL COMMENT '客户地址（选填）',
    `remark`      VARCHAR(500) DEFAULT NULL COMMENT '备注信息（选填）',
    `store_code`  VARCHAR(10)  NOT NULL COMMENT '所属门店编号',
    `total_count` INT          NOT NULL DEFAULT 0 COMMENT '累计洗衣件数（统计用）',
    `total_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计消费金额（统计用）',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone_store` (`phone`,`store_code`) COMMENT '同一门店手机号唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';


-- ============================================================
-- 2. 会员卡表 member_card
--    会员卡类型：member_card_type（已存在：id=1-8.8折卡300元，id=2-6.8折卡500元）
--    注意：折扣率仅用于 price_mode=1（按折扣）的类别；
--         price_mode=2（固定价，如单烫）直接读 clothes_category 的 member_price_300/500
-- ============================================================
DROP TABLE IF EXISTS `member_card`;
CREATE TABLE `member_card` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `card_no`         VARCHAR(30)   NOT NULL COMMENT '会员卡号：MC+日期8位+流水3位，如MC20260812001',
    `customer_id`     BIGINT        NOT NULL COMMENT '客户ID',
    `customer_name`   VARCHAR(50)   DEFAULT NULL COMMENT '客户姓名（冗余）',
    `customer_phone`  VARCHAR(20)   DEFAULT NULL COMMENT '客户电话（冗余）',
    `card_type_id`    BIGINT        NOT NULL COMMENT '会员卡类型ID（关联member_card_type表）',
    `card_type_name`  VARCHAR(50)   NOT NULL COMMENT '卡类型名称（冗余：8.8折卡/6.8折卡）',
    `discount_rate`   DECIMAL(5,2)  NOT NULL COMMENT '折扣率：8.80或6.80（price_mode=1时用）',
    `balance`         DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '卡内余额（可消费余额）',
    `total_recharge`  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计充值金额',
    `total_consume`   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计消费金额',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-停用',
    `store_code`      VARCHAR(10)   NOT NULL COMMENT '办卡门店编号',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '办卡时间',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_card_no` (`card_no`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_customer_phone` (`customer_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员卡表';


-- ============================================================
-- 3. 收衣订单表 laundry_order
--    订单状态：RECEIVED已收衣 → SENT_TO_FACTORY已送厂 → BACK_TO_STORE已回店
--             → NOTIFIED已通知取衣 → PICKED_UP已取衣闭单
--             中途可 CANCELLED 已取消
-- ============================================================
DROP TABLE IF EXISTS `laundry_order`;
CREATE TABLE `laundry_order` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no`          VARCHAR(20)   NOT NULL COMMENT '订单编号：门店3位+日期8位YYYYMMDD+当日流水3位，共14位，如00120260812001',
    `customer_id`       BIGINT        NOT NULL COMMENT '客户ID',
    `customer_name`     VARCHAR(50)   NOT NULL COMMENT '客户姓名（冗余，打印小票直接用）',
    `customer_phone`    VARCHAR(20)   NOT NULL COMMENT '客户电话（冗余）',
    `customer_address`  VARCHAR(255)  DEFAULT NULL COMMENT '客户地址（冗余）',
    `store_code`        VARCHAR(10)   NOT NULL COMMENT '收衣门店编号',
    `total_count`       INT           NOT NULL DEFAULT 0 COMMENT '衣物总件数',
    `total_amount`      DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '原价合计（折扣前总金额）',
    `discount_rate`     DECIMAL(5,2)  NOT NULL DEFAULT 10.00 COMMENT '折扣率：10.00=不打折，8.80=8.8折，6.80=6.8折',
    `discount_amount`   DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '折扣优惠金额（原价合计-折扣后合计）',
    `actual_amount`     DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '折扣后应付总额（加急加价前）',
    `urgent_flag`       TINYINT       NOT NULL DEFAULT 0 COMMENT '是否加急：0否 1是',
    `urgent_surcharge`  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '加急加价金额（actual_amount × 20%）',
    `defect_photos`     TEXT        NULL                 COMMENT '瑕疵拍照数据（JSON数组，存base64）',
    -- 支付相关
    `payment_method`    VARCHAR(20)   NOT NULL COMMENT '支付方式：CASH现金/WECHAT微信/ALIPAY支付宝/MEMBER_CARD会员卡/MIXED组合',
    `member_card_id`    BIGINT        DEFAULT NULL COMMENT '会员卡ID（使用会员卡时不为空）',
    `card_no`           VARCHAR(30)   DEFAULT NULL COMMENT '会员卡号（冗余）',
    `card_deduct`       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '会员卡扣款金额',
    `extra_payment`     DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '补差金额（组合支付时：实付-卡扣）',
    `extra_method`      VARCHAR(20)   DEFAULT NULL COMMENT '补差支付方式：CASH/WECHAT/ALIPAY',
    -- 办卡相关（收衣时同时办卡，办卡金额加到本次应收）
    `new_card_flag`     TINYINT       NOT NULL DEFAULT 0 COMMENT '是否同时办卡：0否 1是',
    `new_card_type_id`  BIGINT        DEFAULT NULL COMMENT '新办卡类型ID',
    `new_card_amount`   DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '新办卡充值金额（300或500）',
    `total_receivable`  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '本次应收总额 = 实付金额actual_amount + 办卡金额new_card_amount',
    `total_paid`        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '本次实收金额（操作员最终收到的钱，可与应收不同，记录欠款）',
    `debt_amount`       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '欠款金额 = 应收 - 实收（留痕用）',
    -- 操作员
    `operator_id`       BIGINT        NOT NULL COMMENT '操作员ID（当前登录用户）',
    `operator_name`     VARCHAR(50)   NOT NULL COMMENT '操作员姓名（冗余，打印小票用）',
    -- 状态与取消
    `status`            VARCHAR(20)   NOT NULL DEFAULT 'RECEIVED' COMMENT '订单状态：RECEIVED/SENT_TO_FACTORY/BACK_TO_STORE/NOTIFIED/PICKED_UP/CANCELLED',
    `cancel_flag`       TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已取消：0否 1是',
    `cancel_time`       DATETIME      DEFAULT NULL COMMENT '取消时间',
    `cancel_operator`   VARCHAR(50)   DEFAULT NULL COMMENT '取消操作人',
    `cancel_reason`     VARCHAR(500)  DEFAULT NULL COMMENT '取消原因',
    `cancel_refund_rate` DECIMAL(5,2) DEFAULT NULL COMMENT '退款比例：100.00=全退/90.00=退90%/70.00=退70%',
    `cancel_refund_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '实际退款金额',
    `cancel_refund_method` VARCHAR(20) DEFAULT NULL COMMENT '退款方式（手动选择）',
    -- 取衣相关
    `pickup_time`       DATETIME      DEFAULT NULL COMMENT '取衣闭单时间',
    `pickup_operator`   VARCHAR(50)   DEFAULT NULL COMMENT '取衣操作人',
    -- 备注
    `remark`            VARCHAR(500)  DEFAULT NULL COMMENT '订单备注（衣物整体说明）',
    `receive_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '收衣时间',
    `create_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_customer_phone` (`customer_phone`),
    KEY `idx_status` (`status`),
    KEY `idx_receive_time` (`receive_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收衣订单表';


-- ============================================================
-- 4. 订单衣物明细表 order_item
--    单件不独立流转状态，状态与订单一致
--    error_back_flag 标记工厂送错的衣物
-- ============================================================
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id`      BIGINT        NOT NULL COMMENT '订单ID',
    `order_no`      VARCHAR(20)   NOT NULL COMMENT '订单编号（冗余）',
    `item_seq`      INT           NOT NULL COMMENT '衣物序号：01,02,03...（同订单内从1递增，用于生成条码）',
    `barcode`       VARCHAR(20)   NOT NULL COMMENT '衣物条码：门店3位+月日4位MMDD+当日流水3位+衣物序号2位，共12位，如001081200101',
    `category_id`   BIGINT        NOT NULL COMMENT '衣物类别ID（关联clothes_category）',
    `category_group` VARCHAR(30)  NOT NULL COMMENT '板块分组（冗余，如CLOTHES）',
    `category_name` VARCHAR(100)  NOT NULL COMMENT '衣物类别名称（冗余：如西装、运动鞋，打印小票用）',
    `quantity`      INT           NOT NULL DEFAULT 1 COMMENT '数量（默认1，一般一件一条码）',
    `unit_price`    DECIMAL(10,2) NOT NULL COMMENT '单价（原价，可手动修改）',
    `member_price`  DECIMAL(10,2) DEFAULT NULL COMMENT '会员价（折扣后的单价，记录留痕）',
    `subtotal`      DECIMAL(10,2) NOT NULL COMMENT '小计 = 单价 × 数量（折扣后金额计入订单）',
    `color`         VARCHAR(30)   DEFAULT NULL COMMENT '颜色（选填）',
    `brand`         VARCHAR(50)   DEFAULT NULL COMMENT '品牌（选填）',
    `defect`        VARCHAR(500)  DEFAULT NULL COMMENT '瑕疵备注（选填，如边部磨破、领口污渍）',
    `special`       VARCHAR(500)  DEFAULT NULL COMMENT '特殊处理要求（选填，如羽绒服清洗、污渍尽洗）',
    -- 上架下架
    `shelf_status`  TINYINT       NOT NULL DEFAULT 0 COMMENT '上架状态：0未上架 1已上架 2已下架',
    `shelf_code`    VARCHAR(30)   DEFAULT NULL COMMENT '货架号/位置（格式：字母-数字-数字，如A-01-03）',
    `on_shelf_time` DATETIME      DEFAULT NULL COMMENT '上架时间',
    `off_shelf_time` DATETIME      DEFAULT NULL COMMENT '下架时间',
    -- 送错回店
    `error_back_flag`   TINYINT    NOT NULL DEFAULT 0 COMMENT '错误回店标记：0正常 1工厂送错',
    `error_back_remark` VARCHAR(500) DEFAULT NULL COMMENT '错误回店说明',
    `error_back_time`   DATETIME   DEFAULT NULL COMMENT '错误回店时间',
    -- 送厂批次关联
    `batch_id`      BIGINT        DEFAULT NULL COMMENT '送厂批次ID（关联factory_batch表，NULL=暂存未送厂）',
    `batch_no`      VARCHAR(30)   DEFAULT NULL COMMENT '送厂批次号（冗余）',
    `send_factory_time` DATETIME  DEFAULT NULL COMMENT '送厂时间',
    `back_store_time`   DATETIME  DEFAULT NULL COMMENT '回店时间',
    `status`        VARCHAR(20)   DEFAULT NULL COMMENT '衣物状态（暂不独立流转，保持NULL或与订单状态一致）',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_barcode` (`barcode`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_batch_id` (`batch_id`),
    KEY `idx_shelf_code` (`shelf_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单衣物明细表';


-- ============================================================
-- 5. 短信记录表 sms_log
--    实际发送待接入阿里云短信，目前只记录
-- ============================================================
DROP TABLE IF EXISTS `sms_log`;
CREATE TABLE `sms_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `phone`       VARCHAR(20)  NOT NULL COMMENT '接收手机号',
    `content`     TEXT         NOT NULL COMMENT '短信内容',
    `sms_type`    VARCHAR(30)  NOT NULL COMMENT '短信类型：RECEIVE收衣通知/BALANCE余额变动/PICKUP取衣通知',
    `order_id`    BIGINT       DEFAULT NULL COMMENT '关联订单ID',
    `order_no`    VARCHAR(20)  DEFAULT NULL COMMENT '关联订单编号',
    `send_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '发送状态：0待发送 1成功 2失败',
    `error_msg`   VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信记录表（待接入阿里云短信）';


-- ============================================================
-- 6. 会员卡充值记录表 member_card_recharge
--    办卡充值、后续追加充值都记在这里
-- ============================================================
DROP TABLE IF EXISTS `member_card_recharge`;
CREATE TABLE `member_card_recharge` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `card_id`         BIGINT        NOT NULL COMMENT '会员卡ID',
    `card_no`         VARCHAR(30)   NOT NULL COMMENT '会员卡号（冗余）',
    `customer_id`     BIGINT        NOT NULL COMMENT '客户ID',
    `customer_name`   VARCHAR(50)   DEFAULT NULL COMMENT '客户姓名（冗余）',
    `recharge_type`   TINYINT       NOT NULL DEFAULT 1 COMMENT '充值类型：1首次办卡充值 2后续追加充值',
    `amount`          DECIMAL(10,2) NOT NULL COMMENT '充值金额',
    `balance_before`  DECIMAL(10,2) NOT NULL COMMENT '充值前余额',
    `balance_after`   DECIMAL(10,2) NOT NULL COMMENT '充值后余额',
    `payment_method`  VARCHAR(20)   NOT NULL COMMENT '充值支付方式：CASH/WECHAT/ALIPAY',
    `order_no`        VARCHAR(20)   DEFAULT NULL COMMENT '关联订单编号（办卡时同时收衣才有）',
    `operator_id`     BIGINT        NOT NULL COMMENT '操作员ID',
    `operator_name`   VARCHAR(50)   NOT NULL COMMENT '操作员姓名',
    `remark`          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '充值时间',
    PRIMARY KEY (`id`),
    KEY `idx_card_id` (`card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员卡充值记录表';


-- ============================================================
-- 7. 送厂批次表 factory_batch
--    装车送厂：批量操作多单，生成一个批次
-- ============================================================
DROP TABLE IF EXISTS `factory_batch`;
CREATE TABLE `factory_batch` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_no`        VARCHAR(30)   NOT NULL COMMENT '批次号：PC+门店3位+日期8位+流水3位，如PC00120260812001',
    `store_code`      VARCHAR(10)   NOT NULL COMMENT '门店编号',
    `order_count`     INT           NOT NULL DEFAULT 0 COMMENT '送厂订单数',
    `item_count`      INT           NOT NULL DEFAULT 0 COMMENT '送厂衣物总件数',
    `send_operator`   VARCHAR(50)   NOT NULL COMMENT '装车送厂操作人',
    `send_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '送厂时间',
    `factory_receiver` VARCHAR(50)  DEFAULT NULL COMMENT '工厂签收人（预留）',
    `factory_receive_time` DATETIME DEFAULT NULL COMMENT '工厂签收时间（预留）',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1送厂中 2已部分回店 3已全部回店',
    `remark`          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送厂批次表';


-- ============================================================
-- 8. 订单操作日志表 order_operate_log
--    记录订单所有状态变更（收衣/送厂/回店/通知取衣/取衣闭单/取消/上架/下架）
-- ============================================================
DROP TABLE IF EXISTS `order_operate_log`;
CREATE TABLE `order_operate_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_id`       BIGINT       NOT NULL COMMENT '订单ID',
    `order_no`       VARCHAR(20)  NOT NULL COMMENT '订单编号（冗余）',
    `item_id`        BIGINT       DEFAULT NULL COMMENT '衣物明细ID（单品操作时有，如上架、单件回店）',
    `barcode`        VARCHAR(20)  DEFAULT NULL COMMENT '衣物条码（单品操作时有）',
    `operate_type`   VARCHAR(30)  NOT NULL COMMENT '操作类型：RECEIVE收衣/SEND送厂/BACK回店/NOTIFY通知取衣/PICKUP取衣闭单/CANCEL取消/ON_SHELF上架/OFF_SHELF下架/ERROR_BACK错误回店/RECHARGE会员卡充值/DEDUCT会员卡扣款',
    `operate_desc`   VARCHAR(500) DEFAULT NULL COMMENT '操作描述（中文说明，如"订单已送厂，批次号PCxxx"）',
    `before_status`  VARCHAR(20)  DEFAULT NULL COMMENT '操作前订单状态',
    `after_status`   VARCHAR(20)  DEFAULT NULL COMMENT '操作后订单状态',
    `amount_change`  DECIMAL(12,2) DEFAULT NULL COMMENT '金额变动（如扣款-52.80、充值+300）',
    `operator_id`    BIGINT       NOT NULL COMMENT '操作人ID',
    `operator_name`  VARCHAR(50)  NOT NULL COMMENT '操作人姓名',
    `operate_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `remark`         VARCHAR(500) DEFAULT NULL COMMENT '备注（如取消原因）',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_item_id` (`item_id`),
    KEY `idx_operate_type` (`operate_type`),
    KEY `idx_operate_time` (`operate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单操作日志表（全流程留痕）';


-- ============================================================
-- 9. 会员卡消费记录表 member_card_consume
--    记录每次从会员卡扣款的明细（可选，但对账方便）
-- ============================================================
DROP TABLE IF EXISTS `member_card_consume`;
CREATE TABLE `member_card_consume` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `card_id`         BIGINT        NOT NULL COMMENT '会员卡ID',
    `card_no`         VARCHAR(30)   NOT NULL COMMENT '会员卡号（冗余）',
    `customer_id`     BIGINT        NOT NULL COMMENT '客户ID',
    `customer_name`   VARCHAR(50)   DEFAULT NULL COMMENT '客户姓名（冗余）',
    `order_id`        BIGINT        DEFAULT NULL COMMENT '关联订单ID',
    `order_no`        VARCHAR(20)   DEFAULT NULL COMMENT '关联订单编号',
    `consume_type`    TINYINT       NOT NULL DEFAULT 1 COMMENT '消费类型：1正常扣款 2取消退款 3其他',
    `amount`          DECIMAL(10,2) NOT NULL COMMENT '消费金额（正数扣款，负数退款）',
    `balance_before`  DECIMAL(10,2) NOT NULL COMMENT '扣前余额',
    `balance_after`   DECIMAL(10,2) NOT NULL COMMENT '扣后余额',
    `operator_id`     BIGINT        NOT NULL COMMENT '操作员ID',
    `operator_name`   VARCHAR(50)   NOT NULL COMMENT '操作员姓名',
    `remark`          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '消费时间',
    PRIMARY KEY (`id`),
    KEY `idx_card_id` (`card_id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员卡消费记录表（对账用）';


-- ============================================================
-- 执行完毕
-- 验证数据（可选执行）：
--   SELECT COUNT(*) AS category_count FROM clothes_category;  -- 预期 66
--   SHOW TABLES;  -- 检查新建的9张表
-- ============================================================
