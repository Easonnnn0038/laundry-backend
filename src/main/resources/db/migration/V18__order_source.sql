ALTER TABLE laundry_order
    ADD COLUMN order_source VARCHAR(20) NOT NULL DEFAULT 'STORE' AFTER order_type;

UPDATE laundry_order o
JOIN pickup_order p ON p.formal_order_id = o.id
SET o.order_source = 'MINIAPP';
