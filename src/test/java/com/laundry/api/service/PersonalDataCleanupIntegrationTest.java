package com.laundry.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PersonalDataCleanupIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "MINIAPP_TEST_DB_PASSWORD", matches = ".+")
    void clearsOnlyDueContactFieldsAndPreservesAmounts() {
        String database = "laundry_privacy_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String options = "?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
        String password = System.getenv("MINIAPP_TEST_DB_PASSWORD");
        var admin = new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://localhost:3306/" + options, "root", password));
        admin.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
        try {
            var source = new DriverManagerDataSource("jdbc:mysql://localhost:3306/" + database + options, "root", password);
            var jdbc = new JdbcTemplate(source);
            assertEquals(database, jdbc.queryForObject("SELECT DATABASE()", String.class));
            jdbc.execute("CREATE TABLE miniapp_deletion_request(id BIGINT PRIMARY KEY,openid VARCHAR(64) NOT NULL,phone VARCHAR(20),status VARCHAR(20),resolved_time DATETIME)");
            jdbc.execute("CREATE TABLE laundry_order(id BIGINT PRIMARY KEY,customer_id BIGINT NOT NULL,customer_name VARCHAR(50) NOT NULL,customer_phone VARCHAR(20) NOT NULL,customer_address VARCHAR(255),remark VARCHAR(500),status VARCHAR(20),pickup_time DATETIME,cancel_time DATETIME,debt_amount DECIMAL(10,2),total_paid DECIMAL(10,2))");
            jdbc.execute("CREATE TABLE pickup_order(id BIGINT PRIMARY KEY,customer_phone VARCHAR(20) NOT NULL,contact_name VARCHAR(50) NOT NULL,pickup_address VARCHAR(255) NOT NULL,remark VARCHAR(500),status VARCHAR(30),formal_order_id BIGINT,update_time DATETIME)");
            jdbc.execute("CREATE TABLE customer(id BIGINT PRIMARY KEY,name VARCHAR(50) NOT NULL,phone VARCHAR(20) NOT NULL,address VARCHAR(255),remark VARCHAR(500))");
            jdbc.execute("CREATE TABLE member_card(id BIGINT PRIMARY KEY,customer_id BIGINT,customer_name VARCHAR(50),customer_phone VARCHAR(20),balance DECIMAL(10,2))");
            jdbc.execute("CREATE TABLE miniapp_user(id BIGINT PRIMARY KEY,phone VARCHAR(20))");
            jdbc.execute("CREATE TABLE customer_notification(id BIGINT PRIMARY KEY,order_id BIGINT,recipient VARCHAR(100))");
            jdbc.execute("CREATE TABLE sms_log(id BIGINT PRIMARY KEY,order_id BIGINT,phone VARCHAR(20) NOT NULL,content TEXT)");
            jdbc.execute("CREATE TABLE deal_voucher(id BIGINT PRIMARY KEY,pickup_order_id BIGINT,customer_phone VARCHAR(20) NOT NULL)");
            jdbc.execute("CREATE TABLE miniapp_address(id BIGINT PRIMARY KEY,customer_phone VARCHAR(20))");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__deletion_request_cleanup.sql")).execute(source);
            jdbc.execute("ALTER TABLE laundry_order ADD COLUMN delivered_time DATETIME, ADD COLUMN delivery_contact_name VARCHAR(50), ADD COLUMN delivery_phone VARCHAR(20), ADD COLUMN delivery_province VARCHAR(30), ADD COLUMN delivery_city VARCHAR(30), ADD COLUMN delivery_district VARCHAR(30), ADD COLUMN delivery_address VARCHAR(255)");
            jdbc.execute("ALTER TABLE pickup_order ADD COLUMN delivery_contact_name VARCHAR(50), ADD COLUMN delivery_phone VARCHAR(20), ADD COLUMN delivery_province VARCHAR(30), ADD COLUMN delivery_city VARCHAR(30), ADD COLUMN delivery_district VARCHAR(30), ADD COLUMN delivery_address VARCHAR(255)");
            jdbc.execute("INSERT INTO miniapp_deletion_request VALUES(7,'openid-1','13800000000','ACCOUNT_CLOSED',NULL)");
            jdbc.execute("INSERT INTO customer VALUES(11,'顾客','13800000000','上门地址',NULL)");
            jdbc.execute("INSERT INTO member_card VALUES(21,11,'顾客','13800000000',0)");
            jdbc.execute("INSERT INTO laundry_order(id,customer_id,customer_name,customer_phone,customer_address,status,pickup_time,debt_amount,total_paid) VALUES(31,11,'顾客','13800000000','上门地址','PICKED_UP',DATE_SUB(NOW(),INTERVAL 7 MONTH),0,88),(32,11,'顾客','13800000000','新地址','PICKED_UP',DATE_SUB(NOW(),INTERVAL 2 MONTH),0,66)");
            jdbc.execute("INSERT INTO pickup_order(id,customer_phone,contact_name,pickup_address,status,formal_order_id,update_time) VALUES(41,'13800000000','顾客','上门地址','CONVERTED',31,NOW())");
            jdbc.execute("INSERT INTO customer_notification VALUES(51,31,'13800000000')");
            jdbc.execute("INSERT INTO sms_log VALUES(61,31,'13800000000','已收衣')");
            jdbc.execute("INSERT INTO deal_voucher VALUES(71,41,'13800000000')");
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            var cleanup = new PersonalDataCleanupService(jdbc);
            assertEquals("WAITING_RETENTION", transaction.execute(s -> cleanup.clearDue(7, "管理员")).get("status"));
            assertNull(jdbc.queryForObject("SELECT customer_phone FROM laundry_order WHERE id=31", String.class));
            assertEquals("13800000000", jdbc.queryForObject("SELECT customer_phone FROM laundry_order WHERE id=32", String.class));
            assertNull(jdbc.queryForObject("SELECT recipient FROM customer_notification WHERE id=51", String.class));
            assertNull(jdbc.queryForObject("SELECT customer_phone FROM deal_voucher WHERE id=71", String.class));
            assertEquals(88, jdbc.queryForObject("SELECT total_paid FROM laundry_order WHERE id=31", java.math.BigDecimal.class).intValueExact());
            jdbc.execute("UPDATE laundry_order SET pickup_time=DATE_SUB(NOW(),INTERVAL 7 MONTH) WHERE id=32");
            assertEquals("COMPLETED", transaction.execute(s -> cleanup.clearDue(7, "管理员")).get("status"));
            assertNull(jdbc.queryForObject("SELECT phone FROM customer WHERE id=11", String.class));
            assertNull(jdbc.queryForObject("SELECT phone FROM miniapp_deletion_request WHERE id=7", String.class));
            assertEquals(66, jdbc.queryForObject("SELECT total_paid FROM laundry_order WHERE id=32", java.math.BigDecimal.class).intValueExact());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM miniapp_deletion_cleanup_audit WHERE request_id=7 AND operator_name='管理员'", Integer.class));
        } finally {
            admin.execute("DROP DATABASE `" + database + "`");
        }
    }
}
