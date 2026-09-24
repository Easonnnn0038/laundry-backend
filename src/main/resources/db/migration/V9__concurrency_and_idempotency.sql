ALTER TABLE laundry_order
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT '客户端幂等请求号' AFTER id,
    ADD UNIQUE KEY uk_order_store_request (store_code, request_id),
    ADD CONSTRAINT chk_order_money_nonnegative CHECK (
        total_amount >= 0 AND actual_amount >= 0 AND total_receivable >= 0
        AND total_paid >= 0 AND total_paid <= total_receivable AND debt_amount >= 0
    );

ALTER TABLE member_card
    ADD COLUMN active_customer_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 1 THEN customer_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_member_card_active_customer (active_customer_id),
    ADD CONSTRAINT chk_member_card_balance_nonnegative CHECK (
        balance >= 0 AND total_recharge >= 0 AND total_consume >= 0
    );

ALTER TABLE member_card_recharge
    ADD COLUMN request_id VARCHAR(80) NULL COMMENT '充值幂等请求号' AFTER id,
    ADD UNIQUE KEY uk_member_recharge_request (request_id),
    ADD CONSTRAINT chk_member_recharge_amount_positive CHECK (amount > 0);

ALTER TABLE member_card_consume
    ADD COLUMN request_id VARCHAR(80) NULL COMMENT '消费幂等请求号' AFTER id,
    ADD UNIQUE KEY uk_member_consume_request (request_id);

ALTER TABLE shelf_position
    ADD UNIQUE KEY uk_shelf_item_once (order_item_id);

ALTER TABLE order_item
    ADD CONSTRAINT chk_order_item_single_piece CHECK (quantity = 1),
    ADD CONSTRAINT chk_order_item_money_nonnegative CHECK (
        unit_price >= 0 AND subtotal >= 0 AND (member_price IS NULL OR member_price >= 0)
    );

CREATE TABLE idempotency_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_scope VARCHAR(80) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    response_json LONGTEXT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_scope_request (request_scope, request_id),
    KEY idx_idempotency_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关键写操作幂等记录';
