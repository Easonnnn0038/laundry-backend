package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.service.PickupService;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pickup")
public class PickupController {
    private final PickupService service;
    private final CurrentUserUtil currentUser;

    public PickupController(PickupService service, CurrentUserUtil currentUser) {
        this.service = service; this.currentUser = currentUser;
    }

    public record IdentifyRequest(@NotBlank String phone, @NotBlank String pickupCode) {}
    public record ScanRequest(@NotBlank String phone, @NotBlank String pickupCode,
                              @NotBlank String barcode) {}

    @PostMapping("/prepare-legacy")
    public Result<Map<String, Integer>> prepareLegacy() {
        return Result.success(Map.of("generated", service.prepareLegacy(currentUser.getStoreCode())));
    }

    @GetMapping("/ready")
    public Result<List<Map<String, Object>>> ready() {
        return Result.success(service.ready(currentUser.getStoreCode()));
    }

    @PostMapping("/lookup")
    public Result<Map<String, Object>> lookup(@Valid @RequestBody IdentifyRequest request) {
        return Result.success(service.lookup(request.phone(), request.pickupCode(), currentUser.getStoreCode()));
    }

    @PostMapping("/scan")
    public Result<Map<String, Object>> scan(@Valid @RequestBody ScanRequest request) {
        return Result.success(service.scan(request.phone(), request.pickupCode(), request.barcode(),
                currentUser.getStoreCode(), currentUser.getOperatorId()));
    }

    @PostMapping("/close")
    public Result<Map<String, Object>> close(@Valid @RequestBody IdentifyRequest request) {
        return Result.success(service.close(request.phone(), request.pickupCode(), currentUser.getStoreCode(),
                currentUser.getOperatorId(), currentUser.getOperatorName()));
    }
}
