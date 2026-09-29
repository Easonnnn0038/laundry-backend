CREATE TABLE miniapp_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    openid VARCHAR(64) NOT NULL,
    unionid VARCHAR(64) NULL,
    phone VARCHAR(20) NULL,
    bind_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_miniapp_user_openid (openid),
    KEY idx_miniapp_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信小程序客户身份';
