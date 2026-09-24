package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.dto.request.LoginRequest;
import com.laundry.api.dto.response.LoginResponse;
import com.laundry.api.entity.User;
import com.laundry.api.service.AuthService;
import com.laundry.api.utils.CurrentUserUtil;
import com.laundry.api.security.LoginAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.AuthenticationException;

/**
 * 认证控制器
 * 提供登录接口
 */
@Tag(name = "认证管理", description = "用户登录、登出等认证相关接口")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    @Autowired
    private LoginAttemptLimiter loginLimiter;

    /**
     * 用户登录
     *
     * @param request 登录请求（username, password）
     * @return 登录响应（token, role, username, realName）
     */
    @Operation(summary = "用户登录", description = "通过用户名和密码登录，返回JWT令牌")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        String ip = clientIp(httpRequest);
        if (loginLimiter.blocked(ip, request.getUsername())) {
            httpResponse.setStatus(429);
            return Result.error(429, "登录失败次数过多，请15分钟后重试");
        }
        try {
            LoginResponse response = authService.login(request);
            loginLimiter.succeeded(ip, request.getUsername());
            return Result.success(response);
        } catch (AuthenticationException e) {
            loginLimiter.failed(ip, request.getUsername());
            throw e;
        }
    }

    /**
     * 获取当前登录用户信息
     * 通过 JWT 令牌解析当前用户，返回用户名、角色、真实姓名
     *
     * @return 用户信息
     */
    @Operation(summary = "获取当前用户信息", description = "根据请求头中的JWT令牌返回当前登录用户信息")
    @GetMapping("/info")
    public Result<LoginResponse> info() {
        User user = currentUserUtil.getUser();
        LoginResponse resp = new LoginResponse();
        resp.setToken(null); // info 接口不返回新 token
        resp.setRole(user.getRole());
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        return Result.success(resp);
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
