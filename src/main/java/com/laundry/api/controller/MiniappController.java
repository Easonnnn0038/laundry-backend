package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.security.MiniappPrincipal;
import com.laundry.api.service.MiniappService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/miniapp")
public class MiniappController {
    private final MiniappService service;
    public MiniappController(MiniappService service) { this.service = service; }

    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return Result.success(service.login(body.get("code")));
    }

    @PostMapping("/auth/bind-phone")
    public Result<Map<String, Object>> bindPhone(@AuthenticationPrincipal MiniappPrincipal user,
                                                  @RequestBody Map<String, String> body) {
        return Result.success(service.bindPhone(user.openid(), body.get("code")));
    }

    @GetMapping("/profile")
    public Result<Map<String, Object>> profile(@AuthenticationPrincipal MiniappPrincipal user) {
        return Result.success(service.profile(user.openid()));
    }

    @GetMapping("/orders")
    public Result<List<Map<String, Object>>> orders(@AuthenticationPrincipal MiniappPrincipal user,
                                                     @RequestParam(defaultValue = "false") boolean history) {
        return Result.success(service.orders(user.phone(), history));
    }

    @GetMapping("/orders/{id}")
    public Result<Map<String, Object>> order(@AuthenticationPrincipal MiniappPrincipal user, @PathVariable Long id) {
        return Result.success(service.order(user.phone(), id));
    }
}
