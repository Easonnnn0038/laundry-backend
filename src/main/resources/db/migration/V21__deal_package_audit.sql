CREATE TABLE deal_package_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    package_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(50) NOT NULL,
    before_snapshot JSON NULL,
    after_snapshot JSON NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_deal_package_audit (package_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团购套餐维护审计';
