package com.laundry.api.service;

import com.laundry.api.security.MiniappPrincipal;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.payments.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class HomeDeliveryWechatPayService {
    private final JdbcTemplate jdbc;
    private final HomeDeliveryPaymentSettlementService settlement;
    private final String appId, merchantId, merchantSerial, privateKeyPath, apiV3Key, notifyUrl;
    private volatile RSAAutoCertificateConfig config;
    private volatile JsapiServiceExtension gateway;

    public HomeDeliveryWechatPayService(JdbcTemplate jdbc, HomeDeliveryPaymentSettlementService settlement,
            @Value("${app.wechat.app-id:}") String appId,
            @Value("${app.wechat.pay.merchant-id:}") String merchantId,
            @Value("${app.wechat.pay.merchant-serial:}") String merchantSerial,
            @Value("${app.wechat.pay.private-key-path:}") String privateKeyPath,
            @Value("${app.wechat.pay.api-v3-key:}") String apiV3Key,
            @Value("${app.wechat.pay.notify-url:}") String notifyUrl) {
        this.jdbc = jdbc; this.settlement = settlement; this.appId = appId;
        this.merchantId = merchantId; this.merchantSerial = merchantSerial;
        this.privateKeyPath = privateKeyPath; this.apiV3Key = apiV3Key; this.notifyUrl = notifyUrl;
    }

    public Map<String, Object> checkout(long orderId, MiniappPrincipal user) {
        Map<String, Object> bill = bill(orderId, user.openid());
        if ("PAID".equals(bill.get("status"))) return Map.of("status", "PAID");
        if (!"AWAITING_PAYMENT".equals(bill.get("billing_status")) || !"PENDING".equals(bill.get("status")))
            throw new IllegalArgumentException("最终账单尚未生成");
        PrepayRequest request = new PrepayRequest();
        request.setAppid(appId); request.setMchid(merchantId);
        request.setDescription("洗护及寄回费用");
        request.setOutTradeNo(String.valueOf(bill.get("out_trade_no")));
        request.setNotifyUrl(notifyUrl);
        Amount amount = new Amount(); amount.setTotal(fen((BigDecimal) bill.get("amount"))); amount.setCurrency("CNY");
        Payer payer = new Payer(); payer.setOpenid(user.openid());
        request.setAmount(amount); request.setPayer(payer);
        PrepayWithRequestPaymentResponse response = gateway().prepayWithRequestPayment(request);
        return Map.of("status", "PENDING", "timeStamp", response.getTimeStamp(),
                "nonceStr", response.getNonceStr(), "package", response.getPackageVal(),
                "signType", response.getSignType(), "paySign", response.getPaySign());
    }

    public Map<String, Object> refresh(long orderId, MiniappPrincipal user) {
        Map<String, Object> bill = bill(orderId, user.openid());
        if (!"PENDING".equals(bill.get("status"))) return Map.of("status", bill.get("status"));
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setOutTradeNo(String.valueOf(bill.get("out_trade_no")));
        request.setMchid(merchantId);
        Transaction result = gateway().queryOrderByOutTradeNo(request);
        if (result.getTradeState() == Transaction.TradeStateEnum.SUCCESS) settlement.settle(result, appId, merchantId);
        return Map.of("status", result.getTradeState() == Transaction.TradeStateEnum.SUCCESS ? "PAID" : "PENDING");
    }

    public void notification(String body, String serial, String nonce, String signature, String timestamp) {
        RequestParam request = new RequestParam.Builder().serialNumber(serial).nonce(nonce)
                .signature(signature).timestamp(timestamp).body(body).build();
        Transaction payment = new NotificationParser(config()).parse(request, Transaction.class);
        if (payment.getTradeState() == Transaction.TradeStateEnum.SUCCESS)
            settlement.settle(payment, appId, merchantId);
    }

    private Map<String, Object> bill(long orderId, String openid) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT hp.out_trade_no,hp.amount,hp.status,o.delivery_billing_status AS billing_status
                FROM home_delivery_payment hp JOIN laundry_order o ON o.id=hp.order_id
                JOIN pickup_order p ON p.formal_order_id=o.id
                WHERE o.id=? AND p.openid=? AND o.fulfillment_method='HOME_DELIVERY'
                """, orderId, openid);
        if (rows.size() != 1) throw new IllegalArgumentException("最终账单尚未生成");
        return rows.get(0);
    }

    private int fen(BigDecimal amount) { return amount.movePointRight(2).intValueExact(); }

    private synchronized RSAAutoCertificateConfig config() {
        if (config == null) {
            if (appId.isBlank() || merchantId.isBlank() || merchantSerial.isBlank()
                    || privateKeyPath.isBlank() || apiV3Key.isBlank() || notifyUrl.isBlank())
                throw new IllegalStateException("微信支付商户凭证尚未配置");
            config = new RSAAutoCertificateConfig.Builder().merchantId(merchantId)
                    .privateKeyFromPath(privateKeyPath).merchantSerialNumber(merchantSerial)
                    .apiV3Key(apiV3Key).build();
        }
        return config;
    }

    private synchronized JsapiServiceExtension gateway() {
        if (gateway == null) gateway = new JsapiServiceExtension.Builder().config(config()).build();
        return gateway;
    }
}
