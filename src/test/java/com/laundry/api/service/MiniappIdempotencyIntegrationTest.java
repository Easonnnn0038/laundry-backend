package com.laundry.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laundry.api.entity.MiniappUser;
import com.laundry.api.mapper.LaundryOrderMapper;
import com.laundry.api.mapper.MiniappUserMapper;
import com.laundry.api.mapper.OrderItemMapper;
import com.laundry.api.mapper.StoreMapper;
import com.laundry.api.security.MiniappPrincipal;
import com.laundry.api.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class MiniappIdempotencyIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "MINIAPP_TEST_DB_PASSWORD", matches = ".+")
    void sameUserAndRequestIdOnlyCreateOneOrderUnderConcurrency() throws Exception {
        var source = new DriverManagerDataSource("jdbc:mysql://localhost:3306/laundry_miniapp_test?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
                "root", System.getenv("MINIAPP_TEST_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        assertEquals("laundry_miniapp_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        schema(jdbc);
        clean(jdbc);
        jdbc.update("INSERT INTO store(id,store_code) VALUES(1,'001')");
        jdbc.update("INSERT INTO clothes_category(id,category_level2,price,unit,price_mode,status,category_group) VALUES(1,'衬衫',20,'件',1,1,'CLOTHES')");
        jdbc.update("INSERT INTO miniapp_user(openid,phone) VALUES('openid-1','13800000001'),('openid-2','13800000002')");
        var service = new PickupOrderService(jdbc, mock(DealPackageService.class));
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        var request = new PickupOrderService.CreateRequest("same-request-0001", "测试", "测试地址",
                LocalDate.now().plusDays(1), "09:00-12:00", "", List.of(new PickupOrderService.ItemRequest(1L, 1)),
                null, null, null, "测试", "13800000001", "安徽省", "宿州市", "埇桥区", "测试寄回地址");
        var user = new MiniappPrincipal("openid-1", "13800000001");
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return transactions.execute(s -> service.create(user, request)); });
            var second = pool.submit(() -> { start.await(); return transactions.execute(s -> service.create(user, request)); });
            start.countDown();
            Map<String, Object> one = first.get(10, TimeUnit.SECONDS), two = second.get(10, TimeUnit.SECONDS);
            assertEquals(one.get("id"), two.get("id"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pickup_order", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pickup_order_item", Integer.class));
            transactions.execute(s -> service.create(new MiniappPrincipal("openid-2", "13800000002"), request));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM pickup_order", Integer.class));

            MiniappUser account = new MiniappUser(); account.setOpenid("openid-1"); account.setPhone("13800000001");
            MiniappUserMapper users = mock(MiniappUserMapper.class);
            when(users.selectOne(any())).thenReturn(account);
            var miniapp = new MiniappService(users, mock(LaundryOrderMapper.class), mock(OrderItemMapper.class),
                    mock(StoreMapper.class), mock(JwtUtil.class), new ObjectMapper(), mock(Environment.class), jdbc);
            assertEquals("PENDING_SERVICE", transactions.execute(s -> miniapp.closeAccount("openid-1")).get("status"));
            jdbc.update("UPDATE pickup_order SET status='PICKED_UP' WHERE openid='openid-1'");
            assertEquals("PENDING_SERVICE", transactions.execute(s -> miniapp.closeAccount("openid-1")).get("status"));
            jdbc.update("INSERT INTO laundry_order(id,status) VALUES(10,'RECEIVED')");
            jdbc.update("UPDATE pickup_order SET status='CONVERTED',formal_order_id=10 WHERE openid='openid-1'");
            assertEquals("PENDING_SERVICE", transactions.execute(s -> miniapp.closeAccount("openid-1")).get("status"));
            jdbc.update("UPDATE laundry_order SET status='PICKED_UP' WHERE id=10");
            transactions.execute(s -> miniapp.closeAccount("openid-1"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM miniapp_user WHERE openid='openid-1'", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pickup_order WHERE openid IS NULL", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM miniapp_deletion_request WHERE openid='openid-1' AND status='PENDING'", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM miniapp_address WHERE customer_phone='13800000001'", Integer.class));
        } finally {
            pool.shutdownNow();
            clean(jdbc);
        }
    }

    private void clean(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM pickup_order_item"); jdbc.update("DELETE FROM pickup_order"); jdbc.update("DELETE FROM miniapp_address");
        jdbc.update("DELETE FROM miniapp_deletion_request"); jdbc.update("DELETE FROM miniapp_user"); jdbc.update("DELETE FROM laundry_order");
        jdbc.update("DELETE FROM clothes_category"); jdbc.update("DELETE FROM member_card"); jdbc.update("DELETE FROM store");
    }

    private void schema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS store(id BIGINT PRIMARY KEY,store_code VARCHAR(32))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS clothes_category(id BIGINT PRIMARY KEY,category_level2 VARCHAR(100),price DECIMAL(10,2),unit VARCHAR(20),price_mode INT,member_price300 DECIMAL(10,2),member_price500 DECIMAL(10,2),status INT,category_group VARCHAR(30))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS member_card(id BIGINT PRIMARY KEY,customer_phone VARCHAR(20),status INT,card_type_name VARCHAR(50),discount_rate DECIMAL(5,2))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS miniapp_user(id BIGINT PRIMARY KEY AUTO_INCREMENT,openid VARCHAR(64) UNIQUE,phone VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS miniapp_deletion_request(id BIGINT PRIMARY KEY AUTO_INCREMENT,openid VARCHAR(64),phone VARCHAR(20),status VARCHAR(20) DEFAULT 'PENDING',request_time DATETIME DEFAULT CURRENT_TIMESTAMP,resolved_time DATETIME)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS laundry_order(id BIGINT PRIMARY KEY,status VARCHAR(30))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS pickup_order(id BIGINT PRIMARY KEY AUTO_INCREMENT,request_id VARCHAR(64),pickup_no VARCHAR(32),openid VARCHAR(64),customer_phone VARCHAR(20),contact_name VARCHAR(50),store_code VARCHAR(32),pickup_address VARCHAR(255),appointment_date DATE,appointment_slot VARCHAR(20),remark VARCHAR(500),status VARCHAR(30),formal_order_id BIGINT,original_amount DECIMAL(10,2),discount_amount DECIMAL(10,2),member_card_id BIGINT,member_card_type VARCHAR(50),discount_rate DECIMAL(5,2),estimated_amount DECIMAL(10,2),paid_amount DECIMAL(10,2),deal_package_id BIGINT,deal_code VARCHAR(40),deal_name VARCHAR(100),deal_price DECIMAL(10,2),deal_platform VARCHAR(30),deal_voucher_code VARCHAR(80),deal_rule_snapshot VARCHAR(500),UNIQUE KEY uk_user_request(openid,request_id),UNIQUE KEY uk_pickup_no(pickup_no))");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pickup_order' AND column_name='formal_order_id'", Integer.class) == 0)
            jdbc.execute("ALTER TABLE pickup_order ADD COLUMN formal_order_id BIGINT");
        for (String column : List.of("delivery_contact_name", "delivery_phone", "delivery_province",
                "delivery_city", "delivery_district", "delivery_address")) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pickup_order' AND column_name=?", Integer.class, column) == 0)
                jdbc.execute("ALTER TABLE pickup_order ADD COLUMN " + column + " VARCHAR(255)");
        }
        jdbc.execute("CREATE TABLE IF NOT EXISTS pickup_order_item(id BIGINT PRIMARY KEY AUTO_INCREMENT,pickup_order_id BIGINT,category_id BIGINT,category_name VARCHAR(100),unit VARCHAR(20),quantity INT,catalog_price DECIMAL(10,2),unit_price DECIMAL(10,2),subtotal DECIMAL(10,2))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS miniapp_address(id BIGINT PRIMARY KEY AUTO_INCREMENT,customer_phone VARCHAR(20),contact_name VARCHAR(50),detail_address VARCHAR(255),is_default TINYINT,UNIQUE KEY uk_address(customer_phone,contact_name,detail_address))");
    }
}
