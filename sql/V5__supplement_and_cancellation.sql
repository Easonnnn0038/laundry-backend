USE `laundry_db`;

ALTER TABLE `order_item`
  ADD COLUMN `parent_item_id` BIGINT DEFAULT NULL COMMENT '补收附件所关联的原衣物',
  ADD COLUMN `item_kind` VARCHAR(20) NOT NULL DEFAULT 'GARMENT' COMMENT 'GARMENT/ACCESSORY',
  ADD KEY `idx_parent_item` (`parent_item_id`);

CREATE TABLE IF NOT EXISTS `supplement_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `order_id` BIGINT NOT NULL,
  `order_no` VARCHAR(20) NOT NULL,
  `parent_item_id` BIGINT NOT NULL,
  `order_item_id` BIGINT NOT NULL,
  `attachment_name` VARCHAR(100) NOT NULL,
  `fee_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `payment_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  `dispatch_status` VARCHAR(20) NOT NULL COMMENT 'MERGED/PENDING/DISPATCHED',
  `package_id` BIGINT DEFAULT NULL,
  `package_no` VARCHAR(40) DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `operator_id` BIGINT NOT NULL,
  `operator_name` VARCHAR(50) NOT NULL,
  `create_time` DATETIME NOT NULL,
  `dispatch_time` DATETIME DEFAULT NULL,
  UNIQUE KEY `uk_supplement_item` (`order_item_id`),
  KEY `idx_supplement_order` (`order_id`, `create_time`),
  KEY `idx_supplement_dispatch` (`dispatch_status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `order_cancel_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `order_id` BIGINT NOT NULL,
  `order_no` VARCHAR(20) NOT NULL,
  `store_code` VARCHAR(10) NOT NULL,
  `reason` VARCHAR(500) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/RESTORED',
  `applicant_id` BIGINT NOT NULL,
  `applicant_name` VARCHAR(50) NOT NULL,
  `apply_time` DATETIME NOT NULL,
  `reviewer_id` BIGINT DEFAULT NULL,
  `reviewer_name` VARCHAR(50) DEFAULT NULL,
  `review_note` VARCHAR(500) DEFAULT NULL,
  `review_time` DATETIME DEFAULT NULL,
  `restore_time` DATETIME DEFAULT NULL,
  KEY `idx_cancel_store_status` (`store_code`, `status`, `apply_time`),
  KEY `idx_cancel_order` (`order_id`, `apply_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
