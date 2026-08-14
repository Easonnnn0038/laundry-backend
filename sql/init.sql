-- ============================================================
-- 洗衣门店管理系统 - 数据库初始化脚本
-- 数据库: laundry_db
-- MySQL 8.0+
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `laundry_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `laundry_db`;

-- ============================================================
-- 1. 用户表 (users)
-- ============================================================
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
    `real_name`   VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
    `role`        VARCHAR(20)  NOT NULL COMMENT '角色：ADMIN-管理员，EMPLOYEE-员工',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `store_id`    VARCHAR(10)  DEFAULT NULL COMMENT '门店编号',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 插入默认用户数据
-- admin123 的 BCrypt 加密值
-- emp123 的 BCrypt 加密值
INSERT INTO `users` (`username`, `password`, `real_name`, `role`, `phone`, `store_id`, `status`, `create_time`, `update_time`) VALUES
('admin',     '$2b$10$lOQoslLJzu0xT5brM9vtReOO1rp/cYiLCsF1bIsfg0UG1umjqPrMnO', '王朋',   'ADMIN',    '13800000001', '001', 1, NOW(), NOW()),
('employee1', '$2b$10$9JyPcBgW58qtmrZP4p3xmOympa/evz6ujT1mXQA3SaZhqhRtVhONi', '邵恒剑', 'EMPLOYEE', '13800000002', '001', 1, NOW(), NOW()),
('employee2', '$2b$10$9JyPcBgW58qtmrZP4p3xmOympa/evz6ujT1mXQA3SaZhqhRtVhONi', '刘洪刚', 'EMPLOYEE', '13800000003', '001', 1, NOW(), NOW());

-- ============================================================
-- 2. 门店表 (store)
-- ============================================================
DROP TABLE IF EXISTS `store`;
CREATE TABLE `store` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `store_code`  VARCHAR(10)  NOT NULL COMMENT '门店编号',
    `store_name`  VARCHAR(100) NOT NULL COMMENT '门店名称',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    `address`     VARCHAR(255) DEFAULT NULL COMMENT '门店地址',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_store_code` (`store_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店表';

-- 插入门店数据
INSERT INTO `store` (`store_code`, `store_name`, `phone`, `address`, `create_time`) VALUES
('001', '小木棒洗衣', '0531-88888888', '金方世纪城民磬路108号', NOW());

-- ============================================================
-- 3. 衣物类别表 (clothes_category)
-- ============================================================
DROP TABLE IF EXISTS `clothes_category`;
CREATE TABLE `clothes_category` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `category_level1` VARCHAR(50)   NOT NULL COMMENT '一级分类（上装类/下装类/家纺类/皮衣护理/包包护理/单烫类）',
    `category_level2` VARCHAR(50)   DEFAULT NULL COMMENT '二级分类（具体衣物名称）',
    `category_level3` VARCHAR(50)   DEFAULT NULL COMMENT '三级分类（面料/规格）',
    `price`           DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '单价',
    `unit`            VARCHAR(10)   DEFAULT '件' COMMENT '计价单位（件/条/个/套）',
    `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_level1` (`category_level1`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='衣物类别表';

-- ============================================================
-- 插入衣物类别数据（共47项：上装21 + 下装10 + 家纺13 + 皮衣护理1 + 包包护理1 + 单烫类1）
-- ============================================================

-- --- 上装类（21种） ---
INSERT INTO `clothes_category` (`category_level1`, `category_level2`, `category_level3`, `price`, `unit`, `status`, `create_time`) VALUES
('上装类', '衬衫',     '普通面料', 15.00, '件', 1, NOW()),
('上装类', 'T恤',      '普通面料', 12.00, '件', 1, NOW()),
('上装类', '西装上衣', '高档面料', 35.00, '件', 1, NOW()),
('上装类', '西装马甲', '高档面料', 20.00, '件', 1, NOW()),
('上装类', '夹克',     '普通面料', 25.00, '件', 1, NOW()),
('上装类', '大衣',     '高档面料', 50.00, '件', 1, NOW()),
('上装类', '风衣',     '高档面料', 40.00, '件', 1, NOW()),
('上装类', '羽绒服',   '冬装',     45.00, '件', 1, NOW()),
('上装类', '棉服',     '冬装',     35.00, '件', 1, NOW()),
('上装类', '毛衣',     '普通面料', 20.00, '件', 1, NOW()),
('上装类', '开衫毛衣', '普通面料', 22.00, '件', 1, NOW()),
('上装类', '卫衣',     '休闲',     18.00, '件', 1, NOW()),
('上装类', 'POLO衫',   '普通面料', 12.00, '件', 1, NOW()),
('上装类', '连衣裙',   '高档面料', 30.00, '件', 1, NOW()),
('上装类', '羊绒衫',   '高档面料', 35.00, '件', 1, NOW()),
('上装类', '针织衫',   '普通面料', 18.00, '件', 1, NOW()),
('上装类', '打底衫',   '普通面料', 12.00, '件', 1, NOW()),
('上装类', '吊带背心', '普通面料', 10.00, '件', 1, NOW()),
('上装类', '皮夹克',   '高档面料', 60.00, '件', 1, NOW()),
('上装类', '派克大衣', '休闲',     45.00, '件', 1, NOW()),
('上装类', '冲锋衣',   '休闲',     35.00, '件', 1, NOW());

-- --- 下装类（10种） ---
INSERT INTO `clothes_category` (`category_level1`, `category_level2`, `category_level3`, `price`, `unit`, `status`, `create_time`) VALUES
('下装类', '西裤',     '高档面料', 20.00, '条', 1, NOW()),
('下装类', '牛仔裤',   '休闲',     15.00, '条', 1, NOW()),
('下装类', '休闲裤',   '休闲',     15.00, '条', 1, NOW()),
('下装类', '短裤',     '休闲',     10.00, '条', 1, NOW()),
('下装类', '裙子',     '高档面料', 25.00, '条', 1, NOW()),
('下装类', '半身裙',   '高档面料', 22.00, '条', 1, NOW()),
('下装类', '运动裤',   '休闲',     15.00, '条', 1, NOW()),
('下装类', '哈伦裤',   '休闲',     15.00, '条', 1, NOW()),
('下装类', '打底裤',   '普通面料', 12.00, '条', 1, NOW()),
('下装类', '工装裤',   '休闲',     18.00, '条', 1, NOW());

-- --- 家纺类（13种） ---
INSERT INTO `clothes_category` (`category_level1`, `category_level2`, `category_level3`, `price`, `unit`, `status`, `create_time`) VALUES
('家纺类', '床单',     '双人', 25.00, '条', 1, NOW()),
('家纺类', '被套',     '双人', 30.00, '条', 1, NOW()),
('家纺类', '枕套',     '普通', 10.00, '个', 1, NOW()),
('家纺类', '床罩',     '双人', 35.00, '条', 1, NOW()),
('家纺类', '毛毯',     '加厚', 45.00, '条', 1, NOW()),
('家纺类', '窗帘',     '普通', 30.00, '条', 1, NOW()),
('家纺类', '沙发套',   '普通', 40.00, '套', 1, NOW()),
('家纺类', '桌布',     '普通', 15.00, '条', 1, NOW()),
('家纺类', '浴巾',     '普通', 15.00, '条', 1, NOW()),
('家纺类', '毛巾',     '普通',  8.00, '条', 1, NOW()),
('家纺类', '地毯',     '加厚', 60.00, '条', 1, NOW()),
('家纺类', '空调罩',   '普通', 12.00, '个', 1, NOW()),
('家纺类', '汽车座套', '普通', 50.00, '套', 1, NOW());

-- --- 皮衣护理（1种） ---
INSERT INTO `clothes_category` (`category_level1`, `category_level2`, `category_level3`, `price`, `unit`, `status`, `create_time`) VALUES
('皮衣护理', '皮衣护理', '默认', 80.00, '件', 1, NOW());

-- --- 包包护理（1种） ---
INSERT INTO `clothes_category` (`category_level1`, `category_level2`, `category_level3`, `price`, `unit`, `status`, `create_time`) VALUES
('包包护理', '包包护理', '默认', 60.00, '个', 1, NOW());

-- --- 单烫类（1种） ---
INSERT INTO `clothes_category` (`category_level1`, `category_level2`, `category_level3`, `price`, `unit`, `status`, `create_time`) VALUES
('单烫类', '单烫', '默认', 8.00, '件', 1, NOW());

-- ============================================================
-- 4. 会员卡类型表 (member_card_type)
-- ============================================================
DROP TABLE IF EXISTS `member_card_type`;
CREATE TABLE `member_card_type` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`          VARCHAR(50)   NOT NULL COMMENT '会员卡名称',
    `amount`        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '充值金额（元）',
    `discount_rate` DECIMAL(5,2)  NOT NULL COMMENT '折扣率（如8.80表示8.8折）',
    `status`        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员卡类型表';

-- 插入会员卡数据
INSERT INTO `member_card_type` (`name`, `amount`, `discount_rate`, `status`, `create_time`) VALUES
('8.8折卡', 300.00, 8.80, 1, NOW()),
('6.8折卡', 500.00, 6.80, 1, NOW());

-- ============================================================
-- 初始化完成
-- ============================================================
-- 验证数据
-- SELECT COUNT(*) AS user_count FROM `users`;                    -- 预期: 3
-- SELECT COUNT(*) AS store_count FROM `store`;                   -- 预期: 1
-- SELECT COUNT(*) AS category_count FROM `clothes_category`;     -- 预期: 47
-- SELECT COUNT(*) AS card_type_count FROM `member_card_type`;    -- 预期: 2
