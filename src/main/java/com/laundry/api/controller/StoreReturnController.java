package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.service.StoreReturnService;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/store-return")
public class StoreReturnController {
    private final StoreReturnService service;
    private final CurrentUserUtil currentUser;
    public StoreReturnController(StoreReturnService service, CurrentUserUtil currentUser) {
        this.service = service; this.currentUser = currentUser;
    }

    public record OrderScanRequest(@NotBlank String barcode) {}
    public record OrderExceptionRequest(@NotBlank String reason) {}

    @GetMapping("/batches")
    public Result<List<Map<String, Object>>> batches() {
        return Result.success(service.batches(currentUser.getStoreCode()));
    }

    @GetMapping("/batches/{batchId}")
    public Result<Map<String, Object>> batch(@PathVariable long batchId) {
        return Result.success(service.batch(batchId, currentUser.getStoreCode()));
    }

    @GetMapping("/batches/{batchId}/orders/{orderNo}")
    public Result<Map<String, Object>> orderDetail(@PathVariable long batchId, @PathVariable String orderNo) {
        return Result.success(service.orderDetail(batchId, orderNo, currentUser.getStoreCode()));
    }

    @PostMapping("/batches/{batchId}/orders/{orderNo}/scan")
    public Result<Map<String, Object>> scanOrder(@PathVariable long batchId, @PathVariable String orderNo,
                                                 @Valid @RequestBody OrderScanRequest request) {
        return Result.success(service.scanOrder(batchId, orderNo, request.barcode(),
                currentUser.getStoreCode(), currentUser.getOperatorId()));
    }

    @PostMapping("/batches/{batchId}/orders/{orderNo}/confirm")
    public Result<Map<String, Object>> confirmOrder(@PathVariable long batchId, @PathVariable String orderNo) {
        return Result.success(service.confirmOrder(batchId, orderNo, currentUser.getStoreCode(),
                currentUser.getOperatorId(), currentUser.getOperatorName()));
    }

    @PostMapping("/batches/{batchId}/orders/{orderNo}/exception")
    public Result<Map<String, Object>> reportOrderException(@PathVariable long batchId, @PathVariable String orderNo,
                                                            @Valid @RequestBody OrderExceptionRequest request) {
        if (!"ADMIN".equals(currentUser.getUser().getRole()))
            throw new org.springframework.security.access.AccessDeniedException("仅管理员可处理错误回店");
        return Result.success(service.reportOrderException(batchId, orderNo, request.reason(),
                currentUser.getStoreCode(), currentUser.getOperatorId()));
    }

}
