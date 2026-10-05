ALTER TABLE pickup_order
    ADD COLUMN original_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER original_amount,
    ADD COLUMN member_card_id BIGINT NULL AFTER discount_amount,
    ADD COLUMN member_card_type VARCHAR(50) NULL AFTER member_card_id,
    ADD COLUMN discount_rate DECIMAL(5,2) NULL AFTER member_card_type;

UPDATE pickup_order SET original_amount=estimated_amount WHERE original_amount=0;

ALTER TABLE pickup_order_item
    ADD COLUMN catalog_price DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER quantity;

UPDATE pickup_order_item SET catalog_price=unit_price WHERE catalog_price=0;
