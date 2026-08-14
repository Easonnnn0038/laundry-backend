package com.laundry.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 参数校验异常 - @RequestBody @Valid
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * 参数校验异常 - @ModelAttribute
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * 认证异常 - 用户名或密码错误
     */
    @ExceptionHandler(BadCredentialsException.class)
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        log.warn("认证失败: 用户名或密码错误");
        return Result.error(401, "用户名或密码错误");
    }

    /**
     * 认证异常 - 用户不存在
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public Result<Void> handleUsernameNotFound(UsernameNotFoundException e) {
        log.warn("认证失败: {}", e.getMessage());
        return Result.error(401, "用户不存在");
    }

    /**
     * 认证异常 - 账号被禁用
     */
    @ExceptionHandler(DisabledException.class)
    public Result<Void> handleDisabled(DisabledException e) {
        log.warn("账号已被禁用");
        return Result.error(403, "账号已被禁用，请联系管理员");
    }

    /**
     * 认证异常 - 其他
     */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthentication(AuthenticationException e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) msg = "用户名或密码错误";
        log.warn("认证异常: {}", msg);
        return Result.error(401, msg);
    }

    /**
     * 非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * 业务运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.error("请求 [{}] {} 发生运行时异常: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return Result.error(500, e.getMessage());
    }

    /**
     * 其他未知异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("请求 [{}] {} 发生异常: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return Result.error(500, "服务器内部错误，请稍后重试");
    }
}
