CREATE TABLE deal_package (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    platform VARCHAR(30) NOT NULL DEFAULT 'DOUYIN',
    price DECIMAL(10,2) NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    required_quantity INT NOT NULL,
    valid_from DATETIME NULL,
    valid_to DATETIME NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    featured TINYINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_deal_package_code (code),
    CONSTRAINT chk_deal_package_money CHECK (price >= 0 AND original_price >= price),
    CONSTRAINT chk_deal_package_quantity CHECK (required_quantity BETWEEN 1 AND 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团购套餐';

CREATE TABLE deal_package_scope (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    package_id BIGINT NOT NULL,
    category_group VARCHAR(30) NOT NULL,
    UNIQUE KEY uk_deal_package_scope (package_id, category_group),
    CONSTRAINT fk_deal_scope_package FOREIGN KEY (package_id) REFERENCES deal_package(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐适用品类';

CREATE TABLE deal_voucher (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    package_id BIGINT NOT NULL,
    platform VARCHAR(30) NOT NULL,
    voucher_code VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REDEEMED',
    pickup_order_id BIGINT NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    redeemed_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_deal_voucher_platform_code (platform, voucher_code),
    UNIQUE KEY uk_deal_voucher_pickup (pickup_order_id),
    KEY idx_deal_voucher_package (package_id, redeemed_time),
    CONSTRAINT fk_deal_voucher_package FOREIGN KEY (package_id) REFERENCES deal_package(id),
    CONSTRAINT fk_deal_voucher_pickup FOREIGN KEY (pickup_order_id) REFERENCES pickup_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团购券码核销';

CREATE TABLE deal_voucher_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    voucher_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    pickup_order_id BIGINT NOT NULL,
    operator_type VARCHAR(20) NOT NULL,
    detail VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_deal_audit_voucher (voucher_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团购券码操作审计';

ALTER TABLE pickup_order
    ADD COLUMN deal_package_id BIGINT NULL AFTER paid_amount,
    ADD COLUMN deal_code VARCHAR(40) NULL AFTER deal_package_id,
    ADD COLUMN deal_name VARCHAR(100) NULL AFTER deal_code,
    ADD COLUMN deal_price DECIMAL(10,2) NULL AFTER deal_name,
    ADD COLUMN deal_platform VARCHAR(30) NULL AFTER deal_price,
    ADD COLUMN deal_voucher_code VARCHAR(80) NULL AFTER deal_platform,
    ADD COLUMN deal_rule_snapshot VARCHAR(500) NULL AFTER deal_voucher_code,
    ADD KEY idx_pickup_deal (deal_package_id, create_time);

INSERT INTO deal_package(code,name,description,price,original_price,required_quantity,featured,sort_order) VALUES
('MIX_1','衣服鞋任选 1 件','不含绒面、皮衣及内胆',10.90,30.00,1,1,1),
('SHOES_3','鞋子清洗 3 双','普通运动鞋，不含绒面',19.90,75.00,3,0,2),
('SHOES_1','洗鞋任选 1 双','普通运动鞋，不含绒面',10.90,30.00,1,0,3),
('DUVET_1','羽绒被清洗 1 件','到店后核对尺寸与材质',28.90,88.00,1,0,4),
('MIX_3','羽绒服、大衣、鞋任选 3 件','不含皮面及内胆',43.90,120.00,3,0,5),
('MIX_2','羽绒服、大衣、鞋任选 2 件','不含皮面及内胆',31.90,80.00,2,0,6),
('MIX_5','衣服鞋任选 5 件','不含绒面、皮衣及内胆',68.90,150.00,5,1,7),
('DOWN_4','羽绒服专业清洗 4 件','毛领与内胆不含',42.90,150.00,4,0,8),
('DOWN_SHOE_2','羽绒服 1 件＋鞋 1 双','不含皮面、毛领及内胆',16.90,55.00,2,1,9),
('DOWN_SHOE_3','羽绒服 2 件＋鞋 1 双','不含绒面、皮面及内胆',28.90,80.00,3,0,10),
('DOWN_2','羽绒服专业清洗 2 件','毛领与内胆不含',20.90,60.00,2,0,11),
('LUXURY_1','奢侈精洗 1 件','到店后核对品牌、面料与工艺',58.90,100.00,1,0,12);

INSERT INTO deal_package_scope(package_id,category_group)
SELECT id,'CLOTHES' FROM deal_package WHERE code IN ('MIX_1','MIX_2','MIX_3','MIX_5','DOWN_4','DOWN_SHOE_2','DOWN_SHOE_3','DOWN_2','LUXURY_1');
INSERT INTO deal_package_scope(package_id,category_group)
SELECT id,'SHOES' FROM deal_package WHERE code IN ('MIX_1','MIX_2','MIX_3','MIX_5','SHOES_1','SHOES_3','DOWN_SHOE_2','DOWN_SHOE_3');
INSERT INTO deal_package_scope(package_id,category_group)
SELECT id,'HOME' FROM deal_package WHERE code='DUVET_1';
