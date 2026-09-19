-- 取衣闭单增量迁移。执行前备份 laundry_db；不可重跑 receive_clothes.sql。
USE `laundry_db`;

ALTER TABLE `laundry_order`
    ADD COLUMN `pickup_code` CHAR(4) DEFAULT NULL COMMENT '整单回店后生成的四位取衣码',
    ADD KEY `idx_pickup_lookup` (`store_code`, `customer_phone`, `pickup_code`, `status`);

CREATE TABLE IF NOT EXISTS `store_pickup_scan` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL,
    `order_item_id` BIGINT NOT NULL,
    `barcode` VARCHAR(20) NOT NULL,
    `operator_id` BIGINT NOT NULL,
    `scan_time` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pickup_order_barcode` (`order_id`, `barcode`),
    KEY `idx_pickup_scan_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='取衣逐件核对记录；全部核对后才允许整单闭单';
