ALTER TABLE pickup_order
    DROP INDEX uk_pickup_order_request,
    MODIFY COLUMN openid VARCHAR(64) NULL,
    ADD UNIQUE KEY uk_pickup_order_user_request (openid, request_id);

ALTER TABLE miniapp_user
    ADD COLUMN token_version INT NOT NULL DEFAULT 0;

CREATE TABLE miniapp_deletion_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    openid VARCHAR(64) NOT NULL,
    phone VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    request_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by VARCHAR(100) NULL,
    review_note VARCHAR(500) NULL,
    reviewed_time DATETIME NULL,
    resolved_time DATETIME NULL,
    KEY idx_miniapp_deletion_openid_status (openid, status, request_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序个人资料删除人工核验申请';
