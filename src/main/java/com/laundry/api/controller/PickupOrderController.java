package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.service.PickupOrderService;
import com.laundry.api.utils.CurrentUserUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pickup-orders")
public class PickupOrderController {
    private final PickupOrderService service;
    private final CurrentUserUtil user;

    public PickupOrderController(PickupOrderService service, CurrentUserUtil user) {
        this.service = service; this.user = user;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String status) {
        return Result.success(service.listForStore(user.getStoreCode(), status));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable long id) {
        return Result.success(service.detailForStore(id, user.getStoreCode()));
    }

    @PostMapping("/{id}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable long id) {
        return Result.success(service.advance(id, user.getStoreCode(), "confirm"));
    }

    @PostMapping("/{id}/picked-up")
    public Result<Map<String, Object>> pickedUp(@PathVariable long id) {
        return Result.success(service.advance(id, user.getStoreCode(), "picked-up"));
    }

}
