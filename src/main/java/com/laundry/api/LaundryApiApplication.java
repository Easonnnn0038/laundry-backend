package com.laundry.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 洗衣门店管理系统 - 后端启动类
 */
@SpringBootApplication
@MapperScan("com.laundry.api.mapper")
public class LaundryApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LaundryApiApplication.class, args);
    }
}
