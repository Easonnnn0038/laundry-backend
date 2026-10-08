CREATE TABLE miniapp_address (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_phone VARCHAR(20) NOT NULL,
    contact_name VARCHAR(50) NOT NULL,
    detail_address VARCHAR(255) NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_miniapp_address (customer_phone, contact_name, detail_address),
    KEY idx_miniapp_address_phone (customer_phone, is_default, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序联系人与上门地址';
