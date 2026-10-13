CREATE TABLE photo_access_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    action VARCHAR(20) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    file_size BIGINT NULL,
    operator_name VARCHAR(50) NULL,
    detail VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_photo_audit_time (create_time),
    KEY idx_photo_audit_file (filename)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店照片访问与清理审计';
