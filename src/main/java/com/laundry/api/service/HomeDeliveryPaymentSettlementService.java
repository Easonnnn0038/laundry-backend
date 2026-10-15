package com.laundry.api.service;

import com.wechat.pay.java.service.payments.model.Transaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class HomeDeliveryPaymentSettlementService {
    private final JdbcTemplate jdbc;

    public HomeDeliveryPaymentSettlementService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void settle(Transaction payment, String appId, String merchantId) {
        if (payment.getTradeState() != Transaction.TradeStateEnum.SUCCESS
                || !appId.equals(payment.getAppid()) || !merchantId.equals(payment.getMchid())
                || payment.getAmount() == null || payment.getAmount().getTotal() == null
                || payment.getPayer() == null || payment.getTransactionId() == null)
            throw new IllegalArgumentException("支付结果不匹配");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT hp.id,hp.order_id,hp.amount,hp.status,hp.wechat_transaction_id,
                       o.debt_amount,o.delivery_billing_status,o.cancel_flag,p.openid
                FROM home_delivery_payment hp JOIN laundry_order o ON o.id=hp.order_id
                JOIN pickup_order p ON p.formal_order_id=o.id
                WHERE hp.out_trade_no=? FOR UPDATE
                """, payment.getOutTradeNo());
        if (rows.size() != 1) throw new IllegalArgumentException("支付订单不存在");
        Map<String, Object> row = rows.get(0);
        if (!String.valueOf(row.get("openid")).equals(payment.getPayer().getOpenid()))
            throw new IllegalArgumentException("付款用户不匹配");
        BigDecimal amount = (BigDecimal) row.get("amount");
        if (amount.movePointRight(2).intValueExact() != payment.getAmount().getTotal())
            throw new IllegalArgumentException("支付金额不匹配");
        if ("PAID".equals(row.get("status"))) {
            if (!payment.getTransactionId().equals(row.get("wechat_transaction_id")))
                throw new IllegalArgumentException("支付流水不匹配");
            return;
        }
        if (!"PENDING".equals(row.get("status")) || !"AWAITING_PAYMENT".equals(row.get("delivery_billing_status"))
                || ((Number) row.get("cancel_flag")).intValue() != 0
                || amount.compareTo((BigDecimal) row.get("debt_amount")) != 0)
            throw new IllegalStateException("订单金额或状态已变化，需要人工核对支付");
        int updated = jdbc.update("""
                UPDATE laundry_order SET total_paid=total_paid+?,debt_amount=debt_amount-?,
                    delivery_billing_status='PAID',status='READY_TO_SHIP',update_time=NOW()
                WHERE id=? AND status='SENT_TO_FACTORY'
                  AND delivery_billing_status='AWAITING_PAYMENT' AND debt_amount=?
                """, amount, amount, row.get("order_id"), amount);
        if (updated != 1) throw new IllegalStateException("订单支付状态更新失败");
        jdbc.update("""
                UPDATE home_delivery_payment SET status='PAID',wechat_transaction_id=?,paid_time=NOW()
                WHERE id=? AND status='PENDING'
                """, payment.getTransactionId(), row.get("id"));
    }
}
