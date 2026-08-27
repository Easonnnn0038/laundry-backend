-- 原子计数器表：解决订单号/卡号生成竞态条件
-- 替代原来的 SELECT COUNT(*) + 1 方案，用 INSERT ON DUPLICATE KEY UPDATE 原子递增
-- 执行方式：直接在 MySQL 中运行

CREATE TABLE IF NOT EXISTS `seq_counter` (
    `counter_key` VARCHAR(50) NOT NULL COMMENT '计数器键：ORDER:门店:日期 或 CARD:日期',
    `seq`         INT         NOT NULL DEFAULT 0 COMMENT '当前序号',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`counter_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='原子序号计数器表';
