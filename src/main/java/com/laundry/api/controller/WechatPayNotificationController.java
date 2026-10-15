package com.laundry.api.controller;

import com.laundry.api.service.HomeDeliveryWechatPayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/wechat")
public class WechatPayNotificationController {
    private final HomeDeliveryWechatPayService pay;

    public WechatPayNotificationController(HomeDeliveryWechatPayService pay) { this.pay = pay; }

    @PostMapping("/notify")
    public ResponseEntity<Void> notify(@RequestBody String body,
            @RequestHeader("Wechatpay-Serial") String serial,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Timestamp") String timestamp) {
        pay.notification(body, serial, nonce, signature, timestamp);
        return ResponseEntity.noContent().build();
    }
}
