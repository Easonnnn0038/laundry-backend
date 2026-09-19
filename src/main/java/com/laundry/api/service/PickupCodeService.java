package com.laundry.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PickupCodeService {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public PickupCodeService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 整单回店后生成；同门店同手机号的未闭单订单不得共用取衣码。 */
    @Transactional(rollbackFor = Exception.class)
    public String ensureCode(long orderId) {
        List<Map<String, Object>> references = jdbc.queryForList(
                "SELECT customer_id FROM laundry_order WHERE id=?", orderId);
        if (references.isEmpty()) throw new IllegalArgumentException("订单不存在");
        long customerId = ((Number) references.get(0).get("customer_id")).longValue();
        // 先锁客户行，串行化同一客户并发回店的取衣码分配。
        List<Long> customer = jdbc.query("SELECT id FROM customer WHERE id=? FOR UPDATE",
                (rs, n) -> rs.getLong(1), customerId);
        if (customer.isEmpty()) throw new IllegalArgumentException("订单客户不存在，无法生成取衣码");
        Map<String, Object> order = jdbc.queryForMap("""
                SELECT id, order_no, customer_phone, store_code, status, pickup_code
                FROM laundry_order WHERE id=? FOR UPDATE
                """, orderId);
        String status = String.valueOf(order.get("status"));
        if (!List.of("BACK_TO_STORE", "NOTIFIED", "SENT_TO_FACTORY").contains(status))
            throw new IllegalArgumentException("整单尚未完成回店签收，不能生成取衣码");
        Integer packageCount = jdbc.queryForObject("SELECT COUNT(*) FROM factory_package WHERE order_id=?",
                Integer.class, orderId);
        Integer pending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM factory_package WHERE order_id=? AND status<>'BACK_TO_STORE'
                """, Integer.class, orderId);
        if (packageCount == null || packageCount < 1 || pending == null || pending > 0)
            throw new IllegalArgumentException("整单大件尚未全部签收，不能生成取衣码");
        if (order.get("pickup_code") != null) return String.valueOf(order.get("pickup_code"));

        String phone = String.valueOf(order.get("customer_phone"));
        String store = String.valueOf(order.get("store_code"));
        for (int attempt = 0; attempt < 100; attempt++) {
            String code = String.format("%04d", random.nextInt(10_000));
            Integer used = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM laundry_order
                    WHERE store_code=? AND customer_phone=? AND pickup_code=?
                      AND status IN ('BACK_TO_STORE', 'NOTIFIED', 'SENT_TO_FACTORY') AND id<>?
                    """, Integer.class, store, phone, code, orderId);
            if (used != null && used > 0) continue;
            jdbc.update("UPDATE laundry_order SET pickup_code=?, update_time=? WHERE id=?",
                    code, LocalDateTime.now(), orderId);
            // TODO(sms-provider): 短信网关接入后发送 send_status=0 的记录；当前不能向客户声称短信已发出。
            jdbc.update("""
                    INSERT INTO sms_log(phone, content, sms_type, order_id, order_no, send_status)
                    VALUES (?, ?, 'PICKUP', ?, ?, 0)
                    """, phone, "您的衣物已回店，四位取衣码：" + code + "。请凭手机号和取衣码到店领取。",
                    orderId, order.get("order_no"));
            return code;
        }
        throw new IllegalArgumentException("暂时无法分配取衣码，请联系管理员");
    }

    /** 仅用于本功能上线前已回店的旧订单；重复调用不会更改已有取衣码。 */
    public int prepareLegacy(String storeCode) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM laundry_order WHERE store_code=? AND pickup_code IS NULL
                  AND status IN ('BACK_TO_STORE', 'NOTIFIED') ORDER BY id
                """, (rs, row) -> rs.getLong(1), storeCode);
        for (Long id : ids) ensureCode(id);
        return ids.size();
    }
}
