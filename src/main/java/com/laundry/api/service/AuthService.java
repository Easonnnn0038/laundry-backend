package com.laundry.api.service;

import com.laundry.api.dto.request.LoginRequest;
import com.laundry.api.dto.response.LoginResponse;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 登录响应（包含JWT令牌）
     */
    LoginResponse login(LoginRequest request);
}
