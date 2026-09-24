package com.laundry.api.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Prevents an empty database from being mistaken for the V7 production baseline. */
@Component
public class SchemaGuard implements ApplicationRunner {
    private static final List<String> REQUIRED_TABLES = List.of(
            "users", "store", "customer", "laundry_order", "order_item",
            "member_card", "factory_package", "shelf_position", "system_event_log"
    );

    private final JdbcTemplate jdbc;

    public SchemaGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN (?,?,?,?,?,?,?,?,?)
                """, Integer.class, REQUIRED_TABLES.toArray());
        if (count == null || count != REQUIRED_TABLES.size()) {
            throw new IllegalStateException("数据库缺少基础表，禁止以V7基线启动；请先恢复或初始化完整数据库");
        }
    }
}
