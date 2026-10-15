package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.service.HomeDeliveryBillingService;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/home-delivery")
public class HomeDeliveryBillingController {
    private final HomeDeliveryBillingService billing;
    private final CurrentUserUtil user;

    public HomeDeliveryBillingController(HomeDeliveryBillingService billing, CurrentUserUtil user) {
        this.billing = billing; this.user = user;
    }

    public record FeeRuleRequest(@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal fee,
                                 boolean enabled) {}

    @GetMapping("/fee-rules")
    public Result<List<Map<String, Object>>> feeRules() { admin(); return Result.success(billing.feeRules()); }

    @PutMapping("/fee-rules/{province}")
    public Result<Map<String, Object>> setFee(@PathVariable String province, @Valid @RequestBody FeeRuleRequest request) {
        admin(); billing.setFee(province, request.fee(), request.enabled()); return Result.success(Map.of());
    }

    @PostMapping("/orders/{id}/issue-bill")
    public Result<Map<String, Object>> issue(@PathVariable long id) {
        admin(); return Result.success(billing.issue(id, user.getStoreCode()));
    }

    private void admin() {
        if (!"ADMIN".equals(user.getUser().getRole())) throw new AccessDeniedException("仅管理员可操作寄回账单");
    }
}
