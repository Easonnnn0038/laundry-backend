package com.laundry.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录响应DTO
 */
@Data
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "JWT令牌")
    private String token;

    @Schema(description = "角色：ADMIN / EMPLOYEE")
    private String role;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;
}
