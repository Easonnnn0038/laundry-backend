package com.laundry.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PickupService {
    private final JdbcTemplate jdbc;
    private final PickupCodeService codeService;

    public PickupService(JdbcTemplate jdbc, PickupCodeService codeService) {
        this.jdbc = jdbc; this.codeService = codeService;
    }

    public int prepareLegacy(String storeCode) { return codeService.prepareLegacy(storeCode); }

    public List<Map<String, Object>> ready(String storeCode) {
        return jdbc.queryForList("""
                SELECT order_no AS orderNo, customer_name AS customerName,
                       customer_phone AS customerPhone, pickup_code AS pickupCode,
                       total_count AS totalCount, debt_amount AS debtAmount, status
                FROM laundry_order WHERE store_code=? AND pickup_code IS NOT NULL
                  AND status IN ('BACK_TO_STORE', 'NOTIFIED') AND cancel_flag=0
                ORDER BY update_time DESC, id DESC LIMIT 100
                """, storeCode);
    }

    public Map<String, Object> lookup(String phone, String code, String storeCode) {
        Map<String, Object> order = findOrder(phone, code, storeCode, false);
        assertCanPickup(order);
        return detail(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scan(String phone, String code, String barcode,
                                    String storeCode, Long operatorId) {
        Map<String, Object> order = findOrder(phone, code, storeCode, true);
        assertCanPickup(order);
        long orderId = ((Number) order.get("id")).longValue();
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT id, status FROM order_item WHERE order_id=? AND barcode=?
                """, orderId, barcode.trim());
        if (items.isEmpty()) throw new IllegalArgumentException("该条码不属于此订单，请核对");
        if (!"BACK_TO_STORE".equals(items.get(0).get("status")))
            throw new IllegalArgumentException("这件衣物尚未完成回店签收");
        Integer scanned = jdbc.queryForObject("""
                SELECT COUNT(*) FROM store_pickup_scan WHERE order_id=? AND barcode=?
                """, Integer.class, orderId, barcode.trim());
        if (scanned != null && scanned > 0) throw new IllegalArgumentException("这件衣物已经核对过");
        jdbc.update("""
                INSERT INTO store_pickup_scan(order_id, order_item_id, barcode, operator_id, scan_time)
                VALUES (?, ?, ?, ?, ?)
                """, orderId, items.get(0).get("id"), barcode.trim(), operatorId, LocalDateTime.now());
        return detail(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> close(String phone, String code, String storeCode,
                                     Long operatorId, String operatorName) {
        Map<String, Object> order = findOrder(phone, code, storeCode, true);
        assertCanPickup(order);
        long orderId = ((Number) order.get("id")).longValue();
        Integer totalItems = jdbc.queryForObject("SELECT COUNT(*) FROM order_item WHERE order_id=?", Integer.class, orderId);
        Integer scanned = jdbc.queryForObject("SELECT COUNT(*) FROM store_pickup_scan WHERE order_id=?", Integer.class, orderId);
        int expected = ((Number) order.get("total_count")).intValue();
        if (expected < 1 || totalItems == null || totalItems != expected || scanned == null || scanned != expected)
            throw new IllegalArgumentException("必须逐件核对整单全部 " + expected + " 件衣物后才能闭单");
        Integer pendingItems = jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_item WHERE order_id=? AND status<>'BACK_TO_STORE'
                """, Integer.class, orderId);
        if (pendingItems != null && pendingItems > 0) throw new IllegalArgumentException("整单衣物尚未全部回店");
        LocalDateTime now = LocalDateTime.now();
        int changed = jdbc.update("""
                UPDATE laundry_order SET status='PICKED_UP', pickup_time=?, pickup_operator=?, update_time=?
                WHERE id=? AND status IN ('BACK_TO_STORE','NOTIFIED') AND debt_amount<=0 AND cancel_flag=0
                """, now, operatorName, now, orderId);
        if (changed != 1) throw new IllegalArgumentException("订单状态已变化，请刷新后重试");
        jdbc.update("""
                UPDATE order_item SET status='PICKED_UP',
                    off_shelf_time=CASE WHEN shelf_status=1 THEN ? ELSE off_shelf_time END,
                    shelf_status=CASE WHEN shelf_status=1 THEN 2 ELSE shelf_status END,
                    update_time=? WHERE order_id=?
                """, now, now, orderId);
        jdbc.update("""
                INSERT INTO order_operate_log(order_id, order_no, operate_type, operate_desc,
                    before_status, after_status, operator_id, operator_name, operate_time, create_time)
                VALUES (?, ?, 'PICKUP', '取衣码、手机号及全部衣物条码核对后整单交付',
                    ?, 'PICKED_UP', ?, ?, ?, ?)
                """, orderId, order.get("order_no"), order.get("status"), operatorId, operatorName, now, now);
        return Map.of("orderNo", order.get("order_no"), "itemCount", expected, "pickupTime", now,
                "status", "PICKED_UP");
    }

    private Map<String, Object> findOrder(String phone, String code, String storeCode, boolean lock) {
        if (phone == null || phone.isBlank() || code == null || !code.matches("\\d{4}"))
            throw new IllegalArgumentException("请输入完整手机号和四位取衣码");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, order_no, customer_name, customer_phone, pickup_code, total_count,
                       debt_amount, status, cancel_flag
                FROM laundry_order
                WHERE store_code=? AND customer_phone=? AND pickup_code=?
                  AND status IN ('BACK_TO_STORE','NOTIFIED') AND cancel_flag=0
                """ + (lock ? " FOR UPDATE" : ""), storeCode, phone.trim(), code);
        if (rows.size() != 1) throw new IllegalArgumentException("手机号或取衣码不正确，或订单尚未回店");
        return rows.get(0);
    }

    private void assertCanPickup(Map<String, Object> order) {
        if (((BigDecimal) order.get("debt_amount")).compareTo(BigDecimal.ZERO) > 0)
            throw new IllegalArgumentException("该订单仍有欠款，请先到其他收款入口结清；本页暂不支持补款");
        long orderId = ((Number) order.get("id")).longValue();
        Integer pendingPackages = jdbc.queryForObject("""
                SELECT COUNT(*) FROM factory_package WHERE order_id=? AND status<>'BACK_TO_STORE'
                """, Integer.class, orderId);
        Integer packageCount = jdbc.queryForObject("SELECT COUNT(*) FROM factory_package WHERE order_id=?",
                Integer.class, orderId);
        if (packageCount == null || packageCount < 1 || pendingPackages == null || pendingPackages > 0)
            throw new IllegalArgumentException("整单大件尚未全部完成回店签收");
    }

    private Map<String, Object> detail(Map<String, Object> order) {
        long orderId = ((Number) order.get("id")).longValue();
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT oi.barcode, oi.category_name AS categoryName, oi.color,
                       oi.shelf_code AS shelfCode,
                       CASE WHEN s.id IS NULL THEN 0 ELSE 1 END AS scanned
                FROM order_item oi
                LEFT JOIN store_pickup_scan s ON s.order_id=oi.order_id AND s.order_item_id=oi.id
                WHERE oi.order_id=? ORDER BY oi.item_seq
                """, orderId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.get("order_no"));
        result.put("customerName", order.get("customer_name"));
        result.put("customerPhone", order.get("customer_phone"));
        result.put("pickupCode", order.get("pickup_code"));
        result.put("totalCount", order.get("total_count"));
        result.put("debtAmount", order.get("debt_amount"));
        result.put("items", items);
        result.put("scannedCount", items.stream().filter(i -> ((Number) i.get("scanned")).intValue() == 1).count());
        return result;
    }
}
