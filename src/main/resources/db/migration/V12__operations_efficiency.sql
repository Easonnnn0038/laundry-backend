INSERT INTO clothes_category(category_group,category_level1,category_level2,price,unit,price_mode,remark,sort_order,status,create_time,update_time)
SELECT 'CUSTOM','自定义','自定义衣物',0,'件',1,'仅管理员在收衣时填写名称和价格',999,1,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM clothes_category WHERE category_group='CUSTOM');

CREATE TABLE category_price_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  category_id BIGINT NOT NULL,
  category_name VARCHAR(100) NOT NULL,
  old_price DECIMAL(10,2) NULL,
  new_price DECIMAL(10,2) NOT NULL,
  operator_id BIGINT NOT NULL,
  operator_name VARCHAR(50) NOT NULL,
  operate_time DATETIME NOT NULL,
  KEY idx_category_price_time(category_id,operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer_notification (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id VARCHAR(80) NOT NULL,
  batch_no VARCHAR(40) NULL,
  store_code VARCHAR(10) NOT NULL,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(20) NOT NULL,
  notification_type VARCHAR(30) NOT NULL COMMENT 'PICKUP_READY/STORE_RETURN_DELAY/STORE_RETURN_COMPLETE/PICKUP_REMINDER',
  channel VARCHAR(20) NOT NULL COMMENT 'SMS/MINIAPP',
  recipient VARCHAR(100) NULL,
  content VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED/NOT_CONFIGURED',
  provider_message_id VARCHAR(100) NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(500) NULL,
  operator_id BIGINT NOT NULL,
  operator_name VARCHAR(50) NOT NULL,
  sent_at DATETIME NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_notification_request_order_channel(request_id,order_id,channel),
  KEY idx_notification_store_status(store_code,status,create_time),
  KEY idx_notification_order(order_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
