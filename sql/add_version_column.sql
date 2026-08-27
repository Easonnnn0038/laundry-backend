-- 乐观锁迁移脚本：为 member_card 表添加 version 列
-- 用途：MyBatis-Plus @Version 乐观锁，解决多收银员并发操作同一会员卡时的余额丢失更新问题
-- 执行方式：直接在 MySQL 中运行此脚本

ALTER TABLE `member_card`
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（并发扣减余额时防止丢失更新）'
    AFTER `status`;
