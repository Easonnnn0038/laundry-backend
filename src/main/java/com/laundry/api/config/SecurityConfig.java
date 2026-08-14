package com.laundry.api.config;

import com.laundry.api.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laundry.api.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Security 配置
 * - 登录接口 POST /api/auth/login 不需要认证
 * - Knife4j 文档相关路径不需要认证
 * - 其他 /api/** 接口需要 JWT 认证
 * - 无状态会话（STATELESS）
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 启用 CORS（使用 CorsConfig 中配置的规则，或默认允许所有方法/头等）
            .cors(cors -> {})
            // 禁用 CSRF（REST API 不需要）
            .csrf(AbstractHttpConfigurer::disable)
            // 禁用表单登录
            .formLogin(AbstractHttpConfigurer::disable)
            // 禁用 HTTP Basic 认证
            .httpBasic(AbstractHttpConfigurer::disable)
            // 无状态会话
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 授权规则
            .authorizeHttpRequests(auth -> auth
                // 登录接口 - 无需认证
                .requestMatchers("/api/auth/login").permitAll()
                // 登录接口的预检 OPTIONS - 无需认证（双保险）
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                // Knife4j / Swagger 文档 - 无需认证
                .requestMatchers(
                        "/doc.html",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/favicon.ico"
                ).permitAll()
                // 照片静态资源 - 无需认证
                .requestMatchers("/photos/**").permitAll()
                // 其他 /api/** 接口 - 需要认证
                .requestMatchers("/api/**").authenticated()
                // 其他请求 - 放行
                .anyRequest().permitAll()
            )
            // 异常处理
            .exceptionHandling(exceptions -> exceptions
                // 未认证（401）
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    Map<String, Object> body = new HashMap<>();
                    body.put("code", 401);
                    body.put("message", "未登录或登录已过期，请重新登录");
                    body.put("data", null);
                    response.getWriter().write(new ObjectMapper().writeValueAsString(body));
                })
                // 权限不足（403）
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    Map<String, Object> body = new HashMap<>();
                    body.put("code", 403);
                    body.put("message", "权限不足，拒绝访问");
                    body.put("data", null);
                    response.getWriter().write(new ObjectMapper().writeValueAsString(body));
                })
            )
            // 在 UsernamePasswordAuthenticationFilter 之前添加 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码编码器 - BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
