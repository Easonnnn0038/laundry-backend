package com.laundry.api.service.impl;

import com.laundry.api.dto.request.LoginRequest;
import com.laundry.api.dto.response.LoginResponse;
import com.laundry.api.entity.User;
import com.laundry.api.security.JwtUtil;
import com.laundry.api.service.AuthService;
import com.laundry.api.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("用户登录: {}", request.getUsername());

        // 使用 AuthenticationManager 进行认证
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword())
        );

        // 认证成功，将认证信息存入 SecurityContext
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 查询用户详情
        User user = userService.getByUsername(request.getUsername());

        // 生成 JWT 令牌
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());

        // 构建响应
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setRole(user.getRole());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());

        log.info("用户 [{}] 登录成功, 角色: {}", user.getUsername(), user.getRole());

        return response;
    }
}
