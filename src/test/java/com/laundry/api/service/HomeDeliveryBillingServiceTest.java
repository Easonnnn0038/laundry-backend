package com.laundry.api.service;

import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.model.TransactionAmount;
import com.wechat.pay.java.service.payments.model.TransactionPayer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HomeDeliveryBillingServiceTest {
    @Test
    void finalBillIncludesConfiguredFeeAndCannotBeIssuedWithoutIt() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> order = Map.of("id", 7L, "order_no", "ORDER-7", "status", "SENT_TO_FACTORY",
                "cancel_flag", 0, "fulfillment_method", "HOME_DELIVERY", "delivery_billing_status", "AWAITING_FEE",
                "delivery_province", "安徽省", "total_receivable", new BigDecimal("80.00"),
                "total_paid", BigDecimal.ZERO, "debt_amount", new BigDecimal("80.00"));
        when(jdbc.queryForList(contains("FROM laundry_order"), eq(7L), eq("001"))).thenReturn(List.of(order));
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM factory_package WHERE"), eq(Integer.class), eq(7L))).thenReturn(1);
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM factory_package fp"), eq(Integer.class), eq(7L))).thenReturn(0);
        HomeDeliveryBillingService service = new HomeDeliveryBillingService(jdbc);
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM factory_package fp"), eq(Integer.class), eq(7L))).thenReturn(1);
        assertThrows(IllegalArgumentException.class, () -> service.issue(7, "001"));
        verify(jdbc, never()).query(contains("delivery_fee_rule"), any(RowMapper.class), eq("安徽省"));
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM factory_package fp"), eq(Integer.class), eq(7L))).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.issue(7, "001"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));

        when(jdbc.query(contains("delivery_fee_rule"), any(RowMapper.class), eq("安徽省")))
                .thenReturn(List.of(new BigDecimal("10.00")));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        Map<String, Object> bill = service.issue(7, "001");
        assertEquals(new BigDecimal("90.00"), bill.get("amountDue"));
        verify(jdbc).update(contains("INSERT INTO home_delivery_payment"), eq(7L), anyString(),
                eq(new BigDecimal("90.00")));
    }

    @Test
    void verifiedPaymentMustMatchTheExactBillAndPayer() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HomeDeliveryPaymentSettlementService service = new HomeDeliveryPaymentSettlementService(jdbc);
        Map<String, Object> bill = Map.of("id", 3L, "order_id", 7L, "amount", new BigDecimal("90.00"),
                "status", "PENDING", "debt_amount", new BigDecimal("90.00"),
                "delivery_billing_status", "AWAITING_PAYMENT", "cancel_flag", 0, "openid", "openid-1");
        when(jdbc.queryForList(contains("home_delivery_payment hp"), eq("LD-test"))).thenReturn(List.of(bill));
        Transaction transaction = new Transaction();
        transaction.setTradeState(Transaction.TradeStateEnum.SUCCESS);
        transaction.setAppid("app-id"); transaction.setMchid("merchant-id");
        transaction.setOutTradeNo("LD-test"); transaction.setTransactionId("wechat-transaction");
        TransactionPayer payer = new TransactionPayer(); payer.setOpenid("openid-1"); transaction.setPayer(payer);
        TransactionAmount amount = new TransactionAmount(); amount.setTotal(8999); transaction.setAmount(amount);
        assertThrows(IllegalArgumentException.class, () -> service.settle(transaction, "app-id", "merchant-id"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));

        amount.setTotal(9000);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        service.settle(transaction, "app-id", "merchant-id");
        verify(jdbc).update(contains("delivery_billing_status='PAID'"), eq(new BigDecimal("90.00")),
                eq(new BigDecimal("90.00")), eq(7L), eq(new BigDecimal("90.00")));
    }
}
