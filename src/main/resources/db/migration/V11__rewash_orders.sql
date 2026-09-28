ALTER TABLE laundry_order
    ADD COLUMN order_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/REWASH' AFTER request_id,
    ADD COLUMN source_order_id BIGINT NULL COMMENT '返洗来源订单ID' AFTER order_type,
    ADD COLUMN source_order_no VARCHAR(20) NULL COMMENT '返洗来源订单号' AFTER source_order_id,
    ADD COLUMN rewash_reason VARCHAR(500) NULL COMMENT '返洗原因' AFTER source_order_no,
    ADD KEY idx_order_rewash_source (source_order_id),
    ADD KEY idx_order_type_time (order_type, receive_time);

ALTER TABLE order_item
    ADD COLUMN source_order_item_id BIGINT NULL COMMENT '返洗来源衣物ID' AFTER order_id,
    ADD KEY idx_item_rewash_source (source_order_item_id);
