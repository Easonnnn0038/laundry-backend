-- V1-V7 were applied manually before Flyway was introduced.
-- Existing databases are baselined at V7; all later schema changes belong here.
CREATE TABLE IF NOT EXISTS schema_installation_guard (
    id TINYINT NOT NULL PRIMARY KEY,
    installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_schema_installation_guard_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Flyway-managed schema marker';

INSERT IGNORE INTO schema_installation_guard (id) VALUES (1);
