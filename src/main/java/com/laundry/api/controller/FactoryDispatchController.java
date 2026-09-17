package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.dto.request.CreateFactoryBatchRequest;
import com.laundry.api.service.FactoryDispatchService;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/factory-dispatch")
public class FactoryDispatchController {
    private final FactoryDispatchService service;
    private final CurrentUserUtil currentUser;

    public FactoryDispatchController(FactoryDispatchService service, CurrentUserUtil currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/eligible-orders")
    public Result<List<Map<String, Object>>> eligibleOrders() {
        return Result.success(service.eligibleOrders(currentUser.getStoreCode()));
    }

    @PostMapping("/create")
    public Result<Map<String, Object>> create(@Valid @RequestBody CreateFactoryBatchRequest request) {
        return Result.success(service.createBatch(request, currentUser.getStoreCode(),
                currentUser.getOperatorId(), currentUser.getOperatorName()));
    }
}

