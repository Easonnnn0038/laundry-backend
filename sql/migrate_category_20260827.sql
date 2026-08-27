USE laundry_db;

-- ============================================================
-- Step 1: 给 clothes_category 表补充缺失字段
-- ============================================================
ALTER TABLE `clothes_category`
    ADD COLUMN `category_group`   VARCHAR(50)   DEFAULT NULL COMMENT '板块分组：CLOTHES/LEATHER/HOME/SHOES/IRON/BAG' AFTER `id`,
    ADD COLUMN `price_mode`       TINYINT       NOT NULL DEFAULT 1 COMMENT '会员价模式：1=按比例折扣 2=固定会员价' AFTER `unit`,
    ADD COLUMN `member_price300`  DECIMAL(10,2) DEFAULT NULL COMMENT '300元卡会员价（固定价模式用）' AFTER `price_mode`,
    ADD COLUMN `member_price500`  DECIMAL(10,2) DEFAULT NULL COMMENT '500元卡会员价（固定价模式用）' AFTER `member_price300`,
    ADD COLUMN `remark`           VARCHAR(200)  DEFAULT NULL COMMENT '备注（如"起"表示起步价）' AFTER `member_price500`,
    ADD COLUMN `sort_order`       INT           NOT NULL DEFAULT 0 COMMENT '排序号' AFTER `remark`,
    ADD COLUMN `update_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`;

-- ============================================================
-- Step 2: 清空旧数据 + 重置自增
-- ============================================================
TRUNCATE TABLE clothes_category;

-- ============================================================
-- Step 3: 插入新版衣物类别（6大板块）
-- ============================================================

-- CLOTHES 衣物类（上装）
INSERT INTO `clothes_category`
(`category_group`,`category_level1`,`category_level2`,`category_level3`,`price`,`unit`,`price_mode`,`sort_order`,`status`,`remark`) VALUES
('CLOTHES','上装','T恤','',15.00,'件',1,1,1,''),
('CLOTHES','上装','衬衫（短）','',18.00,'件',1,2,1,''),
('CLOTHES','上装','衬衫（长）','',20.00,'件',1,3,1,''),
('CLOTHES','上装','POLO衫','',18.00,'件',1,4,1,''),
('CLOTHES','上装','毛衣/针织衫','',28.00,'件',1,5,1,''),
('CLOTHES','上装','羊绒衫','',55.00,'件',1,6,1,''),
('CLOTHES','上装','卫衣','',25.00,'件',1,7,1,''),
('CLOTHES','上装','西装上衣','',45.00,'件',1,8,1,''),
('CLOTHES','上装','西装马甲','',25.00,'件',1,9,1,''),
('CLOTHES','上装','夹克/外套','',38.00,'件',1,10,1,''),
('CLOTHES','上装','风衣','',55.00,'件',1,11,1,''),
('CLOTHES','上装','大衣（普通）','',65.00,'件',1,12,1,''),
('CLOTHES','上装','大衣（长）','',80.00,'件',1,13,1,'起'),
('CLOTHES','上装','羽绒服（短）','',65.00,'件',1,14,1,''),
('CLOTHES','上装','羽绒服（长）','',85.00,'件',1,15,1,'起'),
('CLOTHES','上装','棉服','',55.00,'件',1,16,1,''),
('CLOTHES','上装','羽绒马甲','',38.00,'件',1,17,1,''),
('CLOTHES','上装','皮夹克（普通）','',120.00,'件',1,18,1,'起'),
('CLOTHES','上装','皮夹克（高档）','',180.00,'件',1,19,1,'起'),
('CLOTHES','上装','派克大衣','',85.00,'件',1,20,1,'起'),
('CLOTHES','上装','冲锋衣','',55.00,'件',1,21,1,''),
('CLOTHES','上装','打底衫/吊带','',12.00,'件',1,22,1,'');

-- CLOTHES 衣物类（下装）
INSERT INTO `clothes_category`
(`category_group`,`category_level1`,`category_level2`,`category_level3`,`price`,`unit`,`price_mode`,`sort_order`,`status`,`remark`) VALUES
('CLOTHES','下装','西裤','',22.00,'条',1,30,1,''),
('CLOTHES','下装','休闲裤/牛仔裤','',20.00,'条',1,31,1,''),
('CLOTHES','下装','运动裤','',20.00,'条',1,32,1,''),
('CLOTHES','下装','羽绒裤','',45.00,'条',1,33,1,''),
('CLOTHES','下装','短裤','',15.00,'条',1,34,1,'');

-- CLOTHES 衣物类（裙装）
INSERT INTO `clothes_category`
(`category_group`,`category_level1`,`category_level2`,`category_level3`,`price`,`unit`,`price_mode`,`sort_order`,`status`,`remark`) VALUES
('CLOTHES','裙装','半身裙','',25.00,'条',1,40,1,''),
('CLOTHES','裙装','连衣裙（短）','',35.00,'条',1,41,1,''),
('CLOTHES','裙装','连衣裙（长）','',48.00,'条',1,42,1,'起'),
('CLOTHES','裙装','真丝连衣裙','',88.00,'条',1,43,1,'起');

-- LEATHER 皮衣奢饰品护理
INSERT INTO `clothes_category`
(`category_group`,`category_level1`,`category_level2`,`category_level3`,`price`,`unit`,`price_mode`,`sort_order`,`status`,`remark`) VALUES
('LEATHER','皮衣护理','皮夹克清洗','',120.00,'件',1,1,1,'起'),
('LEATHER','皮衣护理','皮夹克保养上油','',180.00,'件',1,2,1,'起'),
('LEATHER','皮衣护理','皮夹克上色翻新','',300.00,'件',1,3,1,'起'),
('LEATHER','皮衣护理','皮毛一体清洗','',280.00,'件',1,4,1,'起'),
('LEATHER','皮衣护理','皮衣修补','',80.00,'件',2,5,1,'按实际补伤收费'),
('LEATHER','皮衣护理','貂皮/裘皮清洗','',380.00,'件',1,6,1,'起'),
('LEATHER','奢饰品','LV/Coach等品牌包清洗','',280.00,'个',1,7,1,'起'),
('LEATHER','奢饰品','品牌包翻新修复','',380.00,'个',2,8,1,'按损坏程度定价'),
('LEATHER','奢饰品','皮带/腰带护理','',50.00,'条',1,9,1,''),
('LEATHER','奢饰品','钱包护理','',80.00,'个',1,10,1,'起'),
('LEATHER','奢饰品','皮革沙发坐垫','',120.00,'个',1,11,1,'起'),
('LEATHER','奢饰品','汽车真皮座椅','',280.00,'座',1,12,1,'起');

-- HOME 家纺卧室
INSERT INTO `clothes_category`
(`category_group`,`category_level1`,`category_level2`,`category_level3`,`price`,`unit`,`price_mode`,`sort_order`,`status`,`remark`) VALUES
('HOME','床上用品','四件套（纯棉）','',55.00,'套',1,1,1,''),
('HOME','床上用品','四件套（真丝/高档）','',120.00,'套',1,2,1,'起'),
('HOME','床上用品','被套（单件）','',30.00,'件',1,3,1,''),
('HOME','床上用品','床单（单件）','',20.00,'件',1,4,1,''),
('HOME','床上用品','枕套（一对）','',15.00,'对',1,5,1,''),
('HOME','床上用品','棉被/春秋被','',45.00,'条',1,6,1,''),
('HOME','床上用品','羽绒被','',120.00,'条',1,7,1,'起'),
('HOME','床上用品','蚕丝被','',88.00,'条',1,8,1,'起'),
('HOME','窗帘布艺','窗帘（按平方）','',15.00,'平方',1,10,1,'起，上门测量'),
('HOME','窗帘布艺','沙发套','',80.00,'个',1,11,1,'起'),
('HOME','窗帘布艺','靠垫套','',15.00,'个',1,12,1,''),
('HOME','家居服','毛毯/珊瑚绒毯','',45.00,'条',1,13,1,''),
('HOME','家居服','浴巾/毛巾被','',25.00,'条',1,14,1,''),
('HOME','家居服','睡衣套装','',35.00,'套',1,15,1,''),
('HOME','家居服','丝巾/围巾','',25.00,'条',1,16,1,'');

-- SHOES 鞋类
INSERT INTO `clothes_category`
(`category_group`,`category_level1`,`category_level2`,`category_level3`,`price`,`unit`,`price_mode`,`sort_order`,`status`,`remark`) VALUES
('SHOES','鞋类护理','运动鞋/网面鞋','',35.00,'双',1,1,1,''),
('SHOES','鞋类护理','帆布鞋','',30.00,'双',1,2,1,''),
('SHOES','鞋类护理','皮鞋（光面）护理','',45.00,'双',1,3,1,''),
('SHOES','鞋类护理','翻毛鞋/磨砂鞋','',55.00,'双',1,4,1,''),
('SHOES','鞋类护理','雪地靴','',65.00,'双',1,5,1,''),
('SHOES','鞋类护理','靴子（长）','',75.00,'双',1,6,1,'起'),
('SHOES','鞋类护理','高跟鞋/单鞋','',40.00,'双',1,7,1,''),
('SHOES','鞋类护理','凉鞋','',35.00,'双',1,8,1,''),
('SHOES','鞋类护理','拖鞋','',20.00,'双',1,9,1,''),
('SHOES','鞋类护理','皮鞋补胶/粘底','',30.00,'双',2,10,1,'按部位定价'),
('SHOES','鞋类护理','皮鞋补色/翻新','',80.00,'双',2,11,1,'起'),
('SHOES','鞋类护理','球鞋换底/修复','',150.00,'双',2,12,1,'按实际定价');

-- IRON 单烫类
INSERT INTO `clothes_category`
(`category_group`,`category_level1`,`category_level2`,`category_level3`,`price`,`unit`,`price_mode`,`sort_order`,`status`,`remark`) VALUES
('IRON','单烫','衬衫','',8.00,'件',1,1,1,''),
('IRON','单烫','西裤','',8.00,'条',1,2,1,''),
('IRON','单烫','西装上衣','',18.00,'件',1,3,1,''),
('IRON','单烫','西装套装（上衣+裤子）','',22.00,'套',1,4,1,''),
('IRON','单烫','大衣/风衣','',25.00,'件',1,5,1,''),
('IRON','单烫','连衣裙','',15.00,'条',1,6,1,''),
('IRON','单烫','毛衣/针织衫','',10.00,'件',1,7,1,''),
('IRON','单烫','床单/被套','',12.00,'件',1,8,1,''),
('IRON','单烫','婚纱/礼服熨烫','',80.00,'件',2,9,1,'按复杂程度定价'),
('IRON','单烫','批量窗帘熨烫','',8.00,'平方',1,10,1,'起');

-- BAG 包包类
INSERT INTO `clothes_category`
(`category_group`,`category_level1`,`category_level2`,`category_level3`,`price`,`unit`,`price_mode`,`sort_order`,`status`,`remark`) VALUES
('BAG','包包护理','帆布包/书包清洗','',30.00,'个',1,1,1,''),
('BAG','包包护理','普通皮包清洗','',60.00,'个',1,2,1,''),
('BAG','包包护理','高档皮包护理','',120.00,'个',1,3,1,'起'),
('BAG','包包护理','皮包上色翻新','',200.00,'个',2,4,1,'按材质定价'),
('BAG','包包护理','包带更换/修复','',50.00,'个',2,5,1,'按配件定价'),
('BAG','包包护理','包包拉链更换','',30.00,'个',2,6,1,''),
('BAG','包包护理','行李箱清洁','',80.00,'个',1,7,1,'24寸以内'),
('BAG','包包护理','登机箱/行李箱（大）','',120.00,'个',1,8,1,'28寸以上'),
('BAG','包包护理','双肩包','',35.00,'个',1,9,1,''),
('BAG','包包护理','奢饰品包深度清洁','',380.00,'个',2,10,1,'起，免费上门取送');

-- ============================================================
-- Step 4: 修复 member_card_type 表缺失字段
-- ============================================================
ALTER TABLE `member_card_type`
    ADD COLUMN `remark`      VARCHAR(200) DEFAULT NULL COMMENT '说明' AFTER `status`,
    ADD COLUMN `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`;

-- ============================================================
-- Step 5: 验证结果
-- ============================================================
SELECT category_group AS '板块', COUNT(*) AS '项目数'
FROM clothes_category
WHERE status = 1
GROUP BY category_group
ORDER BY FIELD(category_group,'CLOTHES','LEATHER','HOME','SHOES','IRON','BAG');
