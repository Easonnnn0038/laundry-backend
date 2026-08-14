package com.laundry.api.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码加密工具类
 * 基于 Spring Security 的 BCryptPasswordEncoder
 */
@Component
public class PasswordUtil {

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 加密原始密码
     *
     * @param rawPassword 原始密码
     * @return BCrypt加密后的密码
     */
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 验证密码是否匹配
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
