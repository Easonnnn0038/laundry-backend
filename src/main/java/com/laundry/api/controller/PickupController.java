package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.service.PickupService;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

    public record IdentifyRequest(@NotBlank String identifier) {}
    public record CloseRequest(@NotBlank String identifier, @NotEmpty List<Long> itemIds) {}

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
        return Result.success(service.lookup(request.identifier(), currentUser.getStoreCode()));
    }

    @PostMapping("/close")
    public Result<Map<String, Object>> close(@Valid @RequestBody CloseRequest request) {
        return Result.success(service.close(request.identifier(), request.itemIds(), currentUser.getStoreCode(),
                currentUser.getOperatorId(), currentUser.getOperatorName()));
    }
}
