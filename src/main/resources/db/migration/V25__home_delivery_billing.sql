ALTER TABLE pickup_order
    ADD COLUMN delivery_contact_name VARCHAR(50) NULL,
    ADD COLUMN delivery_phone VARCHAR(20) NULL,
    ADD COLUMN delivery_province VARCHAR(30) NULL,
    ADD COLUMN delivery_city VARCHAR(30) NULL,
    ADD COLUMN delivery_district VARCHAR(30) NULL,
    ADD COLUMN delivery_address VARCHAR(255) NULL;

ALTER TABLE laundry_order
    ADD COLUMN fulfillment_method VARCHAR(20) NOT NULL DEFAULT 'STORE_PICKUP',
    ADD COLUMN delivery_contact_name VARCHAR(50) NULL,
    ADD COLUMN delivery_phone VARCHAR(20) NULL,
    ADD COLUMN delivery_province VARCHAR(30) NULL,
    ADD COLUMN delivery_city VARCHAR(30) NULL,
    ADD COLUMN delivery_district VARCHAR(30) NULL,
    ADD COLUMN delivery_address VARCHAR(255) NULL,
    ADD COLUMN delivery_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN delivery_billing_status VARCHAR(20) NULL,
    ADD COLUMN delivered_time DATETIME NULL;

CREATE TABLE delivery_fee_rule (
    province VARCHAR(30) PRIMARY KEY,
    fee DECIMAL(10,2) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_delivery_fee_nonnegative CHECK (fee >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寄回运费省份价目；未配置的省份不可出账单';

CREATE TABLE home_delivery_payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    out_trade_no VARCHAR(64) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    wechat_transaction_id VARCHAR(64) NULL,
    paid_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_home_delivery_payment_order (order_id),
    UNIQUE KEY uk_home_delivery_payment_trade (out_trade_no),
    UNIQUE KEY uk_home_delivery_payment_transaction (wechat_transaction_id),
    CONSTRAINT fk_home_delivery_payment_order FOREIGN KEY (order_id) REFERENCES laundry_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='寄回订单最终账单的微信支付记录';
