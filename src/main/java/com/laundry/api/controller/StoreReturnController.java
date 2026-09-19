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

    public record ScanRequest(@NotBlank String packageNo, @NotBlank String barcode) {}
    public record PackageRequest(@NotBlank String packageNo) {}
    public record ExceptionRequest(@NotBlank String packageNo, @NotBlank String reason) {}

    @GetMapping("/batches")
    public Result<List<Map<String, Object>>> batches() {
        return Result.success(service.batches(currentUser.getStoreCode()));
    }

    @GetMapping("/batches/{batchId}")
    public Result<Map<String, Object>> batch(@PathVariable long batchId) {
        return Result.success(service.batch(batchId, currentUser.getStoreCode()));
    }

    @GetMapping("/batches/{batchId}/packages/{packageNo}")
    public Result<Map<String, Object>> packageDetail(@PathVariable long batchId, @PathVariable String packageNo) {
        return Result.success(service.packageDetail(batchId, packageNo, currentUser.getStoreCode()));
    }

    @PostMapping("/batches/{batchId}/scan")
    public Result<Map<String, Object>> scan(@PathVariable long batchId, @Valid @RequestBody ScanRequest request) {
        return Result.success(service.scan(batchId, request.packageNo(), request.barcode(),
                currentUser.getStoreCode(), currentUser.getOperatorId()));
    }

    @PostMapping("/batches/{batchId}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable long batchId, @Valid @RequestBody PackageRequest request) {
        return Result.success(service.confirm(batchId, request.packageNo(), currentUser.getStoreCode(),
                currentUser.getOperatorId(), currentUser.getOperatorName()));
    }

    @PostMapping("/batches/{batchId}/exception")
    public Result<Map<String, Object>> reportException(@PathVariable long batchId, @Valid @RequestBody ExceptionRequest request) {
        return Result.success(service.reportException(batchId, request.packageNo(), request.reason(),
                currentUser.getStoreCode(), currentUser.getOperatorId()));
    }
}
