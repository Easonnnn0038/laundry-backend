package com.laundry.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HomeDeliveryBillingService {
    private final JdbcTemplate jdbc;

    public HomeDeliveryBillingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> feeRules() {
        return jdbc.queryForList("SELECT province,fee,enabled FROM delivery_fee_rule ORDER BY province");
    }

    public void setFee(String province, BigDecimal fee, boolean enabled) {
        if (province == null || province.isBlank() || province.length() > 30 || fee == null
                || fee.signum() < 0 || fee.scale() > 2)
            throw new IllegalArgumentException("省份或运费不正确");
        jdbc.update("""
                INSERT INTO delivery_fee_rule(province,fee,enabled) VALUES(?,?,?)
                ON DUPLICATE KEY UPDATE fee=VALUES(fee),enabled=VALUES(enabled)
                """, province.trim(), fee, enabled);
    }

    @Transactional
    public Map<String, Object> issue(long orderId, String storeCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,order_no,status,cancel_flag,fulfillment_method,delivery_billing_status,
                       delivery_province,total_receivable,total_paid,debt_amount
                FROM laundry_order WHERE id=? AND store_code=? FOR UPDATE
                """, orderId, storeCode);
        if (rows.isEmpty()) throw new IllegalArgumentException("订单不存在");
        Map<String, Object> order = rows.get(0);
        if (!"HOME_DELIVERY".equals(order.get("fulfillment_method"))
                || !"AWAITING_FEE".equals(order.get("delivery_billing_status"))
                || ((Number) order.get("cancel_flag")).intValue() != 0)
            throw new IllegalArgumentException("订单当前不可生成寄回账单");
        if (!"SENT_TO_FACTORY".equals(order.get("status")))
            throw new IllegalArgumentException("订单尚未送厂或状态已变化");
        Integer readyPackages = jdbc.queryForObject("SELECT COUNT(*) FROM factory_package WHERE order_id=?", Integer.class, orderId);
        Integer unfinishedPackages = jdbc.queryForObject("""
                SELECT COUNT(*) FROM factory_package fp WHERE fp.order_id=? AND
                  (fp.status<>'PACKED' OR fp.expected_item_count<>(
                    SELECT COUNT(*) FROM factory_item_state fis
                    WHERE fis.package_id=fp.id AND fis.current_process='RETURN'))
                """, Integer.class, orderId);
        if (readyPackages == null || readyPackages == 0 || unfinishedPackages == null || unfinishedPackages != 0)
            throw new IllegalArgumentException("整单衣物尚未在工厂完成打包");
        List<BigDecimal> fees = jdbc.query("SELECT fee FROM delivery_fee_rule WHERE province=? AND enabled=1",
                (rs, n) -> rs.getBigDecimal(1), order.get("delivery_province"));
        if (fees.isEmpty()) throw new IllegalArgumentException("该省份尚未配置寄回运费");
        BigDecimal fee = fees.get(0);
        BigDecimal due = ((BigDecimal) order.get("debt_amount")).add(fee);
        if (due.signum() <= 0) throw new IllegalArgumentException("账单金额不正确，请人工核对");
        int changed = jdbc.update("""
                UPDATE laundry_order SET delivery_fee=?,total_receivable=total_receivable+?,
                    debt_amount=debt_amount+?,delivery_billing_status='AWAITING_PAYMENT',update_time=NOW()
                WHERE id=? AND delivery_billing_status='AWAITING_FEE'
                """, fee, fee, fee, orderId);
        if (changed != 1) throw new IllegalArgumentException("账单已生成，请刷新");
        String tradeNo = "LD" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO home_delivery_payment(order_id,out_trade_no,amount,status)
                VALUES(?,?,?,'PENDING')
                """, orderId, tradeNo, due);
        return Map.of("orderNo", order.get("order_no"), "washingFee",
                ((BigDecimal) order.get("total_receivable")).subtract((BigDecimal) order.get("total_paid")),
                "deliveryFee", fee, "amountDue", due, "status", "AWAITING_PAYMENT");
    }
}
