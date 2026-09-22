USE `laundry_db`;

CREATE TABLE IF NOT EXISTS `system_event_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `fingerprint` CHAR(64) NOT NULL,
  `category` VARCHAR(30) NOT NULL COMMENT 'SYSTEM_ERROR/BUSINESS_EXCEPTION/CLIENT_ERROR',
  `level` VARCHAR(10) NOT NULL COMMENT 'ERROR/WARN/INFO',
  `module` VARCHAR(50) NOT NULL,
  `message` VARCHAR(1000) NOT NULL,
  `error_class` VARCHAR(200) DEFAULT NULL,
  `request_method` VARCHAR(10) DEFAULT NULL,
  `request_path` VARCHAR(500) DEFAULT NULL,
  `operator_username` VARCHAR(50) DEFAULT NULL,
  `store_code` VARCHAR(10) DEFAULT NULL,
  `client_version` VARCHAR(100) DEFAULT NULL,
  `occurrence_count` INT NOT NULL DEFAULT 1,
  `first_time` DATETIME NOT NULL,
  `last_time` DATETIME NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PROCESSING/RESOLVED/IGNORED',
  `resolution_note` VARCHAR(1000) DEFAULT NULL,
  `resolver_name` VARCHAR(50) DEFAULT NULL,
  `resolved_time` DATETIME DEFAULT NULL,
  KEY `idx_event_category_status` (`category`, `status`, `last_time`),
  KEY `idx_event_fingerprint` (`fingerprint`, `status`, `last_time`),
  KEY `idx_event_store` (`store_code`, `last_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统诊断事件；不保存请求体、密码或令牌';
