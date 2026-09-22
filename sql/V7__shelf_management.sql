USE `laundry_db`;

CREATE TABLE IF NOT EXISTS `shelf_config` (
  `store_code` VARCHAR(10) NOT NULL PRIMARY KEY,
  `max_shelf_no` INT NOT NULL DEFAULT 1700,
  `update_operator` VARCHAR(50) DEFAULT NULL,
  `update_time` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `shelf_position` (
  `store_code` VARCHAR(10) NOT NULL,
  `shelf_no` INT NOT NULL,
  `status` VARCHAR(20) NOT NULL COMMENT 'RESERVED/OCCUPIED/DISABLED',
  `order_item_id` BIGINT DEFAULT NULL,
  `barcode` VARCHAR(20) DEFAULT NULL,
  `order_id` BIGINT DEFAULT NULL,
  `reservation_mode` VARCHAR(10) DEFAULT NULL COMMENT 'SHELF/MOVE',
  `reserved_until` DATETIME DEFAULT NULL,
  `operator_id` BIGINT DEFAULT NULL,
  `operator_name` VARCHAR(50) DEFAULT NULL,
  `update_time` DATETIME NOT NULL,
  PRIMARY KEY (`store_code`,`shelf_no`),
  KEY `idx_shelf_item` (`order_item_id`,`status`),
  KEY `idx_shelf_order` (`order_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仅记录预留、占用及停用位置；不存在的号码即为空闲';

CREATE TABLE IF NOT EXISTS `shelf_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `store_code` VARCHAR(10) NOT NULL,
  `order_id` BIGINT NOT NULL,
  `order_item_id` BIGINT NOT NULL,
  `order_no` VARCHAR(20) NOT NULL,
  `barcode` VARCHAR(20) NOT NULL,
  `action` VARCHAR(20) NOT NULL COMMENT 'SHELF/MOVE/OFF_SHELF',
  `from_shelf_no` INT DEFAULT NULL,
  `to_shelf_no` INT DEFAULT NULL,
  `operator_id` BIGINT NOT NULL,
  `operator_name` VARCHAR(50) NOT NULL,
  `operate_time` DATETIME NOT NULL,
  KEY `idx_shelf_log_item` (`order_item_id`,`operate_time`),
  KEY `idx_shelf_log_store` (`store_code`,`operate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `shelf_config` (`store_code`,`max_shelf_no`,`update_operator`,`update_time`)
SELECT `store_code`,1700,'系统初始化',NOW() FROM `store`;
