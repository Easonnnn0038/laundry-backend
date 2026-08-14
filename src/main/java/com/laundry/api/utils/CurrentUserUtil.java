package com.laundry.api.utils;

import com.laundry.api.entity.User;
import com.laundry.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 当前登录用户工具类
 * 从 Spring Security 上下文获取当前登录的操作员信息
 */
@Component
public class CurrentUserUtil {

    @Autowired
    private UserService userService;

    /**
     * 获取当前登录用户名
     */
    public String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            throw new RuntimeException("未登录");
        }
        return auth.getName();
    }

    /**
     * 获取当前登录用户完整信息
     */
    public User getUser() {
        String username = getUsername();
        User u = userService.getByUsername(username);
        if (u == null) throw new RuntimeException("登录用户不存在");
        return u;
    }

    /**
     * 获取当前操作员ID
     */
    public Long getOperatorId() {
        return getUser().getId();
    }

    /**
     * 获取当前操作员姓名（真实姓名）
     */
    public String getOperatorName() {
        String real = getUser().getRealName();
        if (real == null || real.isBlank()) return getUsername();
        return real;
    }

    /**
     * 获取当前操作员所属门店编号
     */
    public String getStoreCode() {
        String code = getUser().getStoreId();
        if (code == null || code.isBlank()) return "001"; // 默认001
        return code;
    }
}
