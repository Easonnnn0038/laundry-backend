CREATE TABLE customer_contact_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    store_code VARCHAR(32) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    result VARCHAR(20) NOT NULL,
    note VARCHAR(500) NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(100) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_customer_contact_log_customer (store_code, customer_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店客户联系记录';
