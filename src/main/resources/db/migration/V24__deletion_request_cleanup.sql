ALTER TABLE miniapp_deletion_request
    MODIFY COLUMN openid VARCHAR(64) NULL;

ALTER TABLE laundry_order
    MODIFY COLUMN customer_id BIGINT NULL,
    MODIFY COLUMN customer_name VARCHAR(50) NULL,
    MODIFY COLUMN customer_phone VARCHAR(20) NULL,
    ADD COLUMN personal_data_cleared_at DATETIME NULL;

ALTER TABLE pickup_order
    MODIFY COLUMN customer_phone VARCHAR(20) NULL,
    MODIFY COLUMN contact_name VARCHAR(50) NULL,
    MODIFY COLUMN pickup_address VARCHAR(255) NULL,
    ADD COLUMN personal_data_cleared_at DATETIME NULL;

ALTER TABLE customer
    MODIFY COLUMN name VARCHAR(50) NULL,
    MODIFY COLUMN phone VARCHAR(20) NULL;

ALTER TABLE deal_voucher
    MODIFY COLUMN customer_phone VARCHAR(20) NULL;

ALTER TABLE sms_log
    MODIFY COLUMN phone VARCHAR(20) NULL;

CREATE TABLE miniapp_deletion_cleanup_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    operator_name VARCHAR(100) NOT NULL,
    formal_orders INT NOT NULL,
    pickup_orders INT NOT NULL,
    result_status VARCHAR(30) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_deletion_cleanup_request (request_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人资料到期清理操作审计';
