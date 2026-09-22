-- 门店通知与错误回店。仅执行一次；现有订单数据无需重建。
USE `laundry_db`;

CREATE TABLE IF NOT EXISTS `pickup_notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `order_id` BIGINT NOT NULL,
  `channel` VARCHAR(10) NOT NULL COMMENT 'PHONE/WECHAT',
  `content` VARCHAR(500) NOT NULL,
  `operator_id` BIGINT NOT NULL,
  `operator_name` VARCHAR(50) NOT NULL,
  `notified_at` DATETIME NOT NULL,
  KEY `idx_notify_order` (`order_id`, `notified_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `store_return_error` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `store_code` VARCHAR(10) NOT NULL,
  `package_no` VARCHAR(40) NOT NULL,
  `order_no` VARCHAR(30) DEFAULT NULL,
  `return_batch_id` BIGINT DEFAULT NULL,
  `type` VARCHAR(20) NOT NULL COMMENT 'MISSING/WRONG_ITEM/WRONG_STORE/OTHER',
  `description` VARCHAR(500) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RETURNED/RECHECK',
  `resolution_note` VARCHAR(500) DEFAULT NULL,
  `created_by` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL,
  `resolved_by` BIGINT DEFAULT NULL,
  `resolved_at` DATETIME DEFAULT NULL,
  KEY `idx_error_store_status` (`store_code`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
