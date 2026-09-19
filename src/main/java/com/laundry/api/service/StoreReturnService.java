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
                       SUM(rbp.store_receive_status='RECEIVED') AS receivedCount,
                       SUM(rbp.store_receive_status='EXCEPTION') AS exceptionCount,
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
        batch.put("packages", jdbc.queryForList("""
                SELECT rbp.id, rbp.package_id AS packageId, rbp.package_no AS packageNo,
                       rbp.source_batch_no AS sourceBatchNo, rbp.store_receive_status AS status,
                       rbp.store_receive_time AS receiveTime, rbp.exception_reason AS exceptionReason,
                       fp.order_no AS orderNo,
                       fp.expected_item_count AS expectedCount,
                       (SELECT COUNT(*) FROM factory_store_receive_scan s
                        WHERE s.return_batch_package_id=rbp.id) AS scannedCount
                FROM factory_return_batch_package rbp
                JOIN factory_package fp ON fp.id=rbp.package_id
                WHERE rbp.return_batch_id=? AND rbp.store_code=?
                ORDER BY fp.order_no, fp.package_seq
                """, batchId, storeCode));
        return batch;
    }

    public Map<String, Object> packageDetail(long batchId, String packageNo, String storeCode) {
        Map<String, Object> batch = batch(batchId, storeCode);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> packages = (List<Map<String, Object>>) batch.get("packages");
        Map<String, Object> pkg = packages.stream().filter(p -> packageNo.equals(p.get("packageNo")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("大件不在当前回店批次中"));
        long relationId = ((Number) pkg.get("id")).longValue();
        pkg.put("items", jdbc.queryForList("""
                SELECT fpi.barcode, oi.category_name AS categoryName, oi.color,
                       CASE WHEN s.id IS NULL THEN 0 ELSE 1 END AS scanned
                FROM factory_package_item fpi
                JOIN order_item oi ON oi.id=fpi.order_item_id
                LEFT JOIN factory_store_receive_scan s ON s.return_batch_package_id=?
                    AND s.order_item_id=oi.id
                WHERE fpi.package_id=? ORDER BY oi.item_seq
                """, relationId, pkg.get("packageId")));
        return pkg;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scan(long batchId, String packageNo, String barcode,
                                    String storeCode, Long operatorId) {
        Map<String, Object> pkg = lockedPackage(batchId, packageNo, storeCode);
        if (!"WAIT_SCAN".equals(pkg.get("store_receive_status")) || !"RETURNING".equals(pkg.get("package_status")))
            throw new IllegalArgumentException("此大件已签收或已标记异常");
        long relationId = ((Number) pkg.get("id")).longValue();
        long packageId = ((Number) pkg.get("package_id")).longValue();
        List<Map<String, Object>> matched = jdbc.queryForList("""
                SELECT fpi.order_item_id FROM factory_package_item fpi
                JOIN factory_item_state fis ON fis.order_item_id=fpi.order_item_id
                WHERE fpi.package_id=? AND fpi.barcode=? AND fis.current_process='DONE'
                """, packageId, barcode.trim());
        if (matched.isEmpty()) throw new IllegalArgumentException("条码不属于该大件或尚未完成工厂流程，请核对；错件请报告异常");
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(*) FROM factory_store_receive_scan
                WHERE return_batch_package_id=? AND barcode=?
                """, Integer.class, relationId, barcode.trim());
        if (exists != null && exists > 0) throw new IllegalArgumentException("这件衣物已经核对过，请勿重复录入");
        jdbc.update("""
                INSERT INTO factory_store_receive_scan(return_batch_package_id, package_id,
                    order_item_id, barcode, operator_id, scan_time) VALUES (?, ?, ?, ?, ?, ?)
                """, relationId, packageId, matched.get(0).get("order_item_id"), barcode.trim(), operatorId, LocalDateTime.now());
        return packageDetail(batchId, packageNo, storeCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirm(long batchId, String packageNo, String storeCode,
                                       Long operatorId, String operatorName) {
        Map<String, Object> pkg = lockedPackage(batchId, packageNo, storeCode);
        if (!"WAIT_SCAN".equals(pkg.get("store_receive_status")) || !"RETURNING".equals(pkg.get("package_status")))
            throw new IllegalArgumentException("此大件已签收或已标记异常");
        long relationId = ((Number) pkg.get("id")).longValue();
        long packageId = ((Number) pkg.get("package_id")).longValue();
        int expected = ((Number) pkg.get("expected_item_count")).intValue();
        Integer scanned = jdbc.queryForObject("""
                SELECT COUNT(*) FROM factory_store_receive_scan WHERE return_batch_package_id=?
                """, Integer.class, relationId);
        if (expected < 1 || scanned == null || scanned != expected)
            throw new IllegalArgumentException("必须逐件核对全部 " + expected + " 件后，才能签收整个大件");
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                UPDATE factory_return_batch_package SET store_receive_status='RECEIVED',
                    store_receive_time=?, store_receive_operator_id=? WHERE id=?
                """, now, operatorId, relationId);
        jdbc.update("UPDATE factory_package SET status='BACK_TO_STORE', update_time=? WHERE id=?", now, packageId);
        jdbc.update("""
                UPDATE order_item oi JOIN factory_package_item fpi ON fpi.order_item_id=oi.id
                SET oi.status='BACK_TO_STORE', oi.back_store_time=?, oi.update_time=?
                WHERE fpi.package_id=?
                """, now, now, packageId);
        long orderId = ((Number) pkg.get("order_id")).longValue();
        Integer pending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM factory_package WHERE order_id=? AND status<>'BACK_TO_STORE'
                """, Integer.class, orderId);
        if (pending != null && pending == 0) {
            pickupCodeService.ensureCode(orderId);
            int changed = jdbc.update("UPDATE laundry_order SET status='BACK_TO_STORE', update_time=? WHERE id=? AND status='SENT_TO_FACTORY'",
                    now, orderId);
            if (changed != 1) throw new IllegalArgumentException("订单状态已变化，请刷新后重新核对");
            jdbc.update("""
                    INSERT INTO order_operate_log(order_id, order_no, operate_type, operate_desc,
                        before_status, after_status, operator_id, operator_name, operate_time, create_time)
                    SELECT id, order_no, 'BACK', '全部大件回店签收完成', 'SENT_TO_FACTORY',
                        'BACK_TO_STORE', ?, ?, ?, ? FROM laundry_order WHERE id=?
                    """, operatorId, operatorName, now, now, orderId);
        }
        updateBatchStatus(batchId);
        return packageDetail(batchId, packageNo, storeCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportException(long batchId, String packageNo, String reason,
                                               String storeCode, Long operatorId) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("请填写异常原因");
        if (reason.trim().length() > 500) throw new IllegalArgumentException("异常原因不能超过500字");
        Map<String, Object> pkg = lockedPackage(batchId, packageNo, storeCode);
        if (!"WAIT_SCAN".equals(pkg.get("store_receive_status")) || !"RETURNING".equals(pkg.get("package_status")))
            throw new IllegalArgumentException("该大件已签收或已标记异常");
        jdbc.update("""
                UPDATE factory_return_batch_package SET store_receive_status='EXCEPTION',
                    exception_reason=?, exception_time=?, exception_operator_id=? WHERE id=?
                """, reason.trim(), LocalDateTime.now(), operatorId, pkg.get("id"));
        jdbc.update("UPDATE factory_package SET status='FROZEN', frozen_reason=?, update_time=? WHERE id=?",
                reason.trim(), LocalDateTime.now(), pkg.get("package_id"));
        // TODO(customer-service): 客服模块上线后将此异常同步为正式客服工单；当前先冻结大件并保留原因。
        updateBatchStatus(batchId);
        return packageDetail(batchId, packageNo, storeCode);
    }

    private Map<String, Object> lockedPackage(long batchId, String packageNo, String storeCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT rbp.id, rbp.package_id, rbp.store_receive_status, fp.status AS package_status, fp.order_id,
                       fp.expected_item_count FROM factory_return_batch_package rbp
                JOIN factory_package fp ON fp.id=rbp.package_id
                WHERE rbp.return_batch_id=? AND rbp.package_no=? AND rbp.store_code=? FOR UPDATE
                """, batchId, packageNo.trim(), storeCode);
        if (rows.isEmpty()) throw new IllegalArgumentException("大件码不属于当前门店和回店批次");
        return rows.get(0);
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
