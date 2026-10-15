package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.security.MiniappPrincipal;
import com.laundry.api.service.MiniappService;
import com.laundry.api.service.PickupOrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/miniapp")
public class MiniappController {
    private final MiniappService service;
    private final PickupOrderService pickupOrders;
    private final com.laundry.api.service.HomeDeliveryWechatPayService homeDeliveryPay;
    private final com.laundry.api.service.DealPackageService deals;
    public MiniappController(MiniappService service, PickupOrderService pickupOrders,
                             com.laundry.api.service.DealPackageService deals,
                             com.laundry.api.service.HomeDeliveryWechatPayService homeDeliveryPay) {
        this.service = service; this.pickupOrders = pickupOrders; this.deals = deals;
        this.homeDeliveryPay = homeDeliveryPay;
    }

    @PostMapping("/auth/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return Result.success(service.login(body.get("code")));
    }

    @PostMapping("/auth/test-login")
    public Result<Map<String, Object>> testLogin(@RequestHeader(value = "X-Miniapp-Test-Key", required = false) String key) {
        return Result.success(service.testLogin(key));
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

    @PostMapping("/account/unbind-phone")
    public Result<Map<String, Object>> unbindPhone(@AuthenticationPrincipal MiniappPrincipal user) {
        return Result.success(service.unbindPhone(user.openid()));
    }

    @PostMapping("/account/deletion-request")
    public Result<Map<String, Object>> requestDeletion(@AuthenticationPrincipal MiniappPrincipal user) {
        return Result.success(service.requestDeletion(user.openid()));
    }

    @DeleteMapping("/account")
    public Result<Map<String, Object>> closeAccount(@AuthenticationPrincipal MiniappPrincipal user) {
        return Result.success(service.closeAccount(user.openid()));
    }

    @GetMapping("/member-card")
    public Result<Map<String, Object>> memberCard(@AuthenticationPrincipal MiniappPrincipal user) {
        return Result.success(pickupOrders.memberCard(user.phone()));
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

    @PostMapping("/orders/{id}/test-deliver")
    public Result<Map<String, Object>> testDeliver(@AuthenticationPrincipal MiniappPrincipal user, @PathVariable Long id,
            @RequestHeader(value = "X-Miniapp-Test-Key", required = false) String key) {
        return Result.success(service.testDeliver(user.phone(), id, key));
    }

    @PostMapping("/orders/{id}/pay")
    public Result<Map<String, Object>> pay(@AuthenticationPrincipal MiniappPrincipal user, @PathVariable long id) {
        return Result.success(homeDeliveryPay.checkout(id, user));
    }

    @GetMapping("/orders/{id}/payment-status")
    public Result<Map<String, Object>> paymentStatus(@AuthenticationPrincipal MiniappPrincipal user, @PathVariable long id) {
        return Result.success(homeDeliveryPay.refresh(id, user));
    }

    @GetMapping("/pickup-catalog")
    public Result<List<Map<String, Object>>> pickupCatalog(@AuthenticationPrincipal MiniappPrincipal user) {
        return Result.success(pickupOrders.catalog(user.phone()));
    }

    @GetMapping("/deals")
    public Result<List<Map<String, Object>>> deals() {
        return Result.success(deals.activePackages());
    }

    @GetMapping("/pickup-defaults")
    public Result<Map<String, Object>> pickupDefaults(@AuthenticationPrincipal MiniappPrincipal user) {
        return Result.success(pickupOrders.defaults(user));
    }

    @GetMapping("/addresses")
    public Result<List<Map<String, Object>>> addresses(@AuthenticationPrincipal MiniappPrincipal user) {
        return Result.success(pickupOrders.addresses(user.phone()));
    }

    @PostMapping("/addresses")
    public Result<Map<String, Object>> createAddress(@AuthenticationPrincipal MiniappPrincipal user,
            @Valid @RequestBody PickupOrderService.AddressRequest request) {
        return Result.success(pickupOrders.createAddress(user.phone(), request));
    }

    @PutMapping("/addresses/{id}")
    public Result<Map<String, Object>> updateAddress(@AuthenticationPrincipal MiniappPrincipal user, @PathVariable long id,
            @Valid @RequestBody PickupOrderService.AddressRequest request) {
        return Result.success(pickupOrders.updateAddress(user.phone(), id, request));
    }

    @DeleteMapping("/addresses/{id}")
    public Result<Map<String, Object>> deleteAddress(@AuthenticationPrincipal MiniappPrincipal user, @PathVariable long id) {
        pickupOrders.deleteAddress(user.phone(), id);
        return Result.success(Map.of());
    }

    @PostMapping("/addresses/{id}/default")
    public Result<Map<String, Object>> defaultAddress(@AuthenticationPrincipal MiniappPrincipal user, @PathVariable long id) {
        pickupOrders.setDefaultAddress(user.phone(), id);
        return Result.success(Map.of());
    }

    @PostMapping("/pickup-orders")
    public Result<Map<String, Object>> createPickupOrder(@AuthenticationPrincipal MiniappPrincipal user,
            @Valid @RequestBody PickupOrderService.CreateRequest request) {
        return Result.success(pickupOrders.create(user, request));
    }

    @GetMapping("/pickup-orders")
    public Result<List<Map<String, Object>>> pickupOrders(@AuthenticationPrincipal MiniappPrincipal user) {
        return Result.success(pickupOrders.listForUser(user.openid()));
    }

    @GetMapping("/pickup-orders/{id}")
    public Result<Map<String, Object>> pickupOrder(@AuthenticationPrincipal MiniappPrincipal user, @PathVariable long id) {
        return Result.success(pickupOrders.detailForUser(id, user.openid()));
    }

}
