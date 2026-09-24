package com.laundry.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class StoreReturnService {
    private final JdbcTemplate jdbc;
    private final PickupCodeService pickupCodeService;
    public StoreReturnService(JdbcTemplate jdbc, PickupCodeService pickupCodeService) {
        this.jdbc = jdbc; this.pickupCodeService = pickupCodeService;
    }

    public List<Map<String, Object>> batches(String storeCode) {
        return jdbc.queryForList("""
                SELECT rb.id, rb.return_batch_no AS batchNo, rb.dispatch_time AS dispatchTime,
                       COUNT(*) AS packageCount,
                       COUNT(DISTINCT fp.order_no) AS orderCount,
                       SUM(rbp.store_receive_status='RECEIVED') AS receivedCount,
                       COUNT(DISTINCT CASE WHEN rbp.store_receive_status='EXCEPTION' THEN fp.order_no END) AS exceptionCount,
                       SUM(fp.expected_item_count) AS itemCount
                FROM factory_return_batch rb
                JOIN factory_return_batch_package rbp ON rbp.return_batch_id=rb.id
                JOIN factory_package fp ON fp.id=rbp.package_id
                WHERE rbp.store_code=?
                GROUP BY rb.id, rb.return_batch_no, rb.dispatch_time
                ORDER BY rb.dispatch_time DESC, rb.id DESC
                LIMIT 100
                """, storeCode);
    }

    public Map<String, Object> batch(long batchId, String storeCode) {
        List<Map<String, Object>> batches = jdbc.queryForList("""
                SELECT rb.id, rb.return_batch_no AS batchNo, rb.dispatch_time AS dispatchTime,
                       rb.status FROM factory_return_batch rb
                WHERE rb.id=? AND EXISTS (SELECT 1 FROM factory_return_batch_package rbp
                    WHERE rbp.return_batch_id=rb.id AND rbp.store_code=?)
                """, batchId, storeCode);
        if (batches.isEmpty()) throw new IllegalArgumentException("回店批次不存在或不属于当前门店");
        Map<String, Object> batch = batches.get(0);
        batch.put("orders", jdbc.queryForList("""
                SELECT fp.order_no AS orderNo,
                       SUM(fp.expected_item_count) AS expectedCount,
                       SUM((SELECT COUNT(*) FROM factory_store_receive_scan s
                            WHERE s.return_batch_package_id=rbp.id)) AS scannedCount,
                       CASE WHEN SUM(rbp.store_receive_status='EXCEPTION')>0 THEN 'EXCEPTION'
                            WHEN SUM(rbp.store_receive_status='RECEIVED')=COUNT(*) THEN 'RECEIVED'
                            ELSE 'WAIT_SCAN' END AS status,
                       GROUP_CONCAT(DISTINCT rbp.exception_reason SEPARATOR '；') AS exceptionReason
                FROM factory_return_batch_package rbp
                JOIN factory_package fp ON fp.id=rbp.package_id
                WHERE rbp.return_batch_id=? AND rbp.store_code=?
                GROUP BY fp.order_no ORDER BY fp.order_no
                """, batchId, storeCode));
        return batch;
    }

    public Map<String, Object> orderDetail(long batchId, String orderNo, String storeCode) {
        Map<String, Object> batch = batch(batchId, storeCode);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) batch.get("orders");
        Map<String, Object> order = orders.stream().filter(o -> orderNo.trim().equals(o.get("orderNo")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("订单不在当前回店批次中"));
        order.put("items", jdbc.queryForList("""
                SELECT fpi.barcode, oi.category_name AS categoryName, oi.color,
                       CASE WHEN s.id IS NULL THEN 0 ELSE 1 END AS scanned
                FROM factory_return_batch_package rbp
                JOIN factory_package fp ON fp.id=rbp.package_id
                JOIN factory_package_item fpi ON fpi.package_id=fp.id
                JOIN order_item oi ON oi.id=fpi.order_item_id
                LEFT JOIN factory_store_receive_scan s ON s.return_batch_package_id=rbp.id
                    AND s.order_item_id=oi.id
                WHERE rbp.return_batch_id=? AND rbp.store_code=? AND fp.order_no=?
                ORDER BY oi.item_seq
                """, batchId, storeCode, orderNo.trim()));
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scanOrder(long batchId, String orderNo, String barcode,
                                         String storeCode, Long operatorId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT rbp.id, rbp.package_id, rbp.store_receive_status,
                       fp.status AS package_status, fpi.order_item_id, fis.current_process
                FROM factory_return_batch_package rbp
                JOIN factory_package fp ON fp.id=rbp.package_id
                JOIN factory_package_item fpi ON fpi.package_id=fp.id
                LEFT JOIN factory_item_state fis ON fis.order_item_id=fpi.order_item_id
                WHERE rbp.return_batch_id=? AND rbp.store_code=? AND fp.order_no=? AND fpi.barcode=?
                FOR UPDATE
                """, batchId, storeCode, orderNo.trim(), barcode.trim());
        if (rows.isEmpty()) throw new IllegalArgumentException("衣物码不属于该订单，请核对；错件请报告异常");
        Map<String, Object> item = rows.get(0);
        if (!"WAIT_SCAN".equals(item.get("store_receive_status")) || !"RETURNING".equals(item.get("package_status")))
            throw new IllegalArgumentException("这件衣物所属订单已签收或已标记异常");
        if (!"DONE".equals(item.get("current_process")))
            throw new IllegalArgumentException("这件衣物尚未完成工厂流程");
        long relationId = ((Number) item.get("id")).longValue();
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(*) FROM factory_store_receive_scan
                WHERE return_batch_package_id=? AND barcode=?
                """, Integer.class, relationId, barcode.trim());
        if (exists != null && exists > 0) throw new IllegalArgumentException("这件衣物已经核对过，请勿重复录入");
        jdbc.update("""
                INSERT INTO factory_store_receive_scan(return_batch_package_id, package_id,
                    order_item_id, barcode, operator_id, scan_time) VALUES (?, ?, ?, ?, ?, ?)
                """, relationId, item.get("package_id"), item.get("order_item_id"), barcode.trim(), operatorId, LocalDateTime.now());
        return orderDetail(batchId, orderNo, storeCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmOrder(long batchId, String orderNo, String storeCode,
                                            Long operatorId, String operatorName) {
        List<Map<String, Object>> packages = lockedOrderPackages(batchId, orderNo, storeCode);
        for (Map<String, Object> pkg : packages) {
            String receiveStatus = String.valueOf(pkg.get("store_receive_status"));
            if ("RECEIVED".equals(receiveStatus)) continue;
            if (!"WAIT_SCAN".equals(receiveStatus) || !"RETURNING".equals(pkg.get("package_status")))
                throw new IllegalArgumentException("该订单包含异常或不可签收的衣物");
            int expected = ((Number) pkg.get("expected_item_count")).intValue();
            Integer scanned = jdbc.queryForObject("SELECT COUNT(*) FROM factory_store_receive_scan WHERE return_batch_package_id=?",
                    Integer.class, pkg.get("id"));
            if (expected < 1 || scanned == null || scanned != expected)
                throw new IllegalArgumentException("必须逐件核对该订单全部衣物后才能签收");
        }
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> pkg : packages) {
            if ("RECEIVED".equals(pkg.get("store_receive_status"))) continue;
            jdbc.update("""
                    UPDATE factory_return_batch_package SET store_receive_status='RECEIVED',
                        store_receive_time=?, store_receive_operator_id=? WHERE id=?
                    """, now, operatorId, pkg.get("id"));
            jdbc.update("UPDATE factory_package SET status='BACK_TO_STORE', update_time=? WHERE id=?", now, pkg.get("package_id"));
            jdbc.update("""
                    UPDATE order_item oi JOIN factory_package_item fpi ON fpi.order_item_id=oi.id
                    SET oi.status='BACK_TO_STORE', oi.back_store_time=?, oi.update_time=?
                    WHERE fpi.package_id=?
                    """, now, now, pkg.get("package_id"));
        }
        long orderId = ((Number) packages.get(0).get("order_id")).longValue();
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM factory_package WHERE order_id=? AND status<>'BACK_TO_STORE'",
                Integer.class, orderId);
        if (pending != null && pending == 0) finishOrder(orderId, operatorId, operatorName, now);
        updateBatchStatus(batchId);
        return orderDetail(batchId, orderNo, storeCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportOrderException(long batchId, String orderNo, String reason,
                                                    String storeCode, Long operatorId) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("请填写异常原因");
        if (reason.trim().length() > 500) throw new IllegalArgumentException("异常原因不能超过500字");
        List<Map<String, Object>> packages = lockedOrderPackages(batchId, orderNo, storeCode);
        boolean changed = false;
        Object errorPackageId = null;
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> pkg : packages) {
            if (!"WAIT_SCAN".equals(pkg.get("store_receive_status")) || !"RETURNING".equals(pkg.get("package_status"))) continue;
            jdbc.update("""
                    UPDATE factory_return_batch_package SET store_receive_status='EXCEPTION',
                        exception_reason=?, exception_time=?, exception_operator_id=? WHERE id=?
                    """, reason.trim(), now, operatorId, pkg.get("id"));
            jdbc.update("UPDATE factory_package SET status='FROZEN', frozen_reason=?, update_time=? WHERE id=?",
                    reason.trim(), now, pkg.get("package_id"));
            if (errorPackageId == null) errorPackageId = pkg.get("package_id");
            changed = true;
        }
        if (!changed) throw new IllegalArgumentException("该订单已经签收或已标记异常");
        jdbc.update("""
                INSERT INTO store_return_error(store_code,package_no,order_no,return_batch_id,type,description,
                    created_by,created_at) SELECT ?,package_no,order_no,?,'OTHER',?,?,? FROM factory_package WHERE id=?
                """, storeCode, batchId, reason.trim(), operatorId, now, errorPackageId);
        updateBatchStatus(batchId);
        return orderDetail(batchId, orderNo, storeCode);
    }

    private List<Map<String, Object>> lockedOrderPackages(long batchId, String orderNo, String storeCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT rbp.id, rbp.package_id, rbp.store_receive_status,
                       fp.status AS package_status, fp.order_id, fp.expected_item_count
                FROM factory_return_batch_package rbp
                JOIN factory_package fp ON fp.id=rbp.package_id
                WHERE rbp.return_batch_id=? AND rbp.store_code=? AND fp.order_no=?
                ORDER BY fp.package_seq FOR UPDATE
                """, batchId, storeCode, orderNo.trim());
        if (rows.isEmpty()) throw new IllegalArgumentException("订单不在当前门店和回店批次中");
        return rows;
    }

    private void finishOrder(long orderId, Long operatorId, String operatorName, LocalDateTime now) {
        pickupCodeService.ensureCode(orderId);
        int changed = jdbc.update("UPDATE laundry_order SET status='BACK_TO_STORE', update_time=? WHERE id=? AND status='SENT_TO_FACTORY'",
                now, orderId);
        if (changed != 1) throw new IllegalArgumentException("订单状态已变化，请刷新后重新核对");
        jdbc.update("""
                INSERT INTO order_operate_log(order_id, order_no, operate_type, operate_desc,
                    before_status, after_status, operator_id, operator_name, operate_time, create_time)
                SELECT id, order_no, 'BACK', '订单全部衣物回店签收完成', 'SENT_TO_FACTORY',
                    'BACK_TO_STORE', ?, ?, ?, ? FROM laundry_order WHERE id=?
                """, operatorId, operatorName, now, now, orderId);
    }

    private void updateBatchStatus(long batchId) {
        jdbc.update("""
                UPDATE factory_return_batch rb SET status=CASE
                    WHEN (SELECT COUNT(*) FROM factory_return_batch_package p
                          WHERE p.return_batch_id=rb.id AND p.store_receive_status='RECEIVED')=rb.package_count
                    THEN 'RECEIVED'
                    WHEN (SELECT COUNT(*) FROM factory_return_batch_package p
                          WHERE p.return_batch_id=rb.id AND p.store_receive_status='RECEIVED')>0
                    THEN 'PART_RECEIVED' ELSE 'DISPATCHED' END,
                    update_time=? WHERE rb.id=?
                """, LocalDateTime.now(), batchId);
    }
}
