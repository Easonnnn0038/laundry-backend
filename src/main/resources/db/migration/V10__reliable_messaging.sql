CREATE TABLE mq_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    store_code VARCHAR(20) NULL,
    exchange_name VARCHAR(120) NOT NULL,
    routing_key VARCHAR(120) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_time DATETIME NULL,
    last_error VARCHAR(1000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mq_outbox_event (event_id),
    KEY idx_mq_outbox_pending (status, next_attempt_time),
    KEY idx_mq_outbox_store (store_code, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务消息发件箱';

CREATE TABLE mq_consumed_event (
    consumer_name VARCHAR(80) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(64) NULL,
    status VARCHAR(20) NOT NULL,
    last_error VARCHAR(1000) NULL,
    processed_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_mq_consumed_status (status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息消费幂等记录';
