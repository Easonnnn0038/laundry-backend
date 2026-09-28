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
    private final ShelfService shelfService;
    public StoreReturnService(JdbcTemplate jdbc, PickupCodeService pickupCodeService, ShelfService shelfService) {
        this.jdbc = jdbc; this.pickupCodeService = pickupCodeService; this.shelfService = shelfService;
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
                       oi.shelf_code AS shelfCode, oi.shelf_status AS shelfStatus,
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
                                         String storeCode, Long operatorId, String operatorName) {
        List<Map<String, Object>> packages = lockedOrderPackages(batchId, orderNo, storeCode);
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
        if (exists != null && exists > 0) throw new IllegalArgumentException("这件衣物已经扫码上架，请勿重复录入");
        Map<String, Object> shelf = shelfService.occupyOnReturn(
                ((Number) item.get("order_item_id")).longValue(), storeCode, operatorId, operatorName);
        jdbc.update("""
                INSERT INTO factory_store_receive_scan(return_batch_package_id, package_id,
                    order_item_id, barcode, operator_id, scan_time) VALUES (?, ?, ?, ?, ?, ?)
                """, relationId, item.get("package_id"), item.get("order_item_id"), barcode.trim(), operatorId, LocalDateTime.now());
        boolean autoCompleted = allPackagesScanned(packages);
        if (autoCompleted) completeOrder(batchId, packages, operatorId, operatorName);
        Map<String, Object> result = orderDetail(batchId, orderNo, storeCode);
        result.put("assignedShelfNo", shelf.get("shelfNo"));
        result.put("scannedBarcode", barcode.trim());
        result.put("autoCompleted", autoCompleted);
        return result;
    }

    private boolean allPackagesScanned(List<Map<String, Object>> packages) {
        for (Map<String, Object> pkg : packages) {
            String receiveStatus = String.valueOf(pkg.get("store_receive_status"));
            if ("RECEIVED".equals(receiveStatus)) continue;
            if (!"WAIT_SCAN".equals(receiveStatus) || !"RETURNING".equals(pkg.get("package_status")))
                throw new IllegalArgumentException("该订单包含异常或不可签收的衣物");
            int expected = ((Number) pkg.get("expected_item_count")).intValue();
            Integer scanned = jdbc.queryForObject("SELECT COUNT(*) FROM factory_store_receive_scan WHERE return_batch_package_id=?",
                    Integer.class, pkg.get("id"));
            if (expected < 1 || scanned == null || scanned != expected) return false;
        }
        return true;
    }

    private void completeOrder(long batchId, List<Map<String, Object>> packages,
                               Long operatorId, String operatorName) {
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
        Map<String,Object> order=jdbc.queryForMap("SELECT id,order_no,order_type,source_order_id,store_code FROM laundry_order WHERE id=?",orderId);
        if ("STORE_RETURN".equals(order.get("order_type"))) {
            finishStoreReturn(order,operatorId,operatorName,now);
            return;
        }
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

    private void finishStoreReturn(Map<String,Object> childOrder,Long operatorId,String operatorName,LocalDateTime now){
        long childId=((Number)childOrder.get("id")).longValue();
        long sourceOrderId=((Number)childOrder.get("source_order_id")).longValue();
        List<Map<String,Object>> items=jdbc.queryForList("""
            SELECT c.id AS childId,c.shelf_code AS shelfCode,c.on_shelf_time AS onShelfTime,
                   s.id AS sourceId,s.barcode AS sourceBarcode
            FROM order_item c JOIN order_item s ON s.id=c.source_order_item_id WHERE c.order_id=?
            """,childId);
        for(Map<String,Object> item:items){
            jdbc.update("""
                UPDATE shelf_position SET order_item_id=?,barcode=?,order_id=?,update_time=?
                WHERE store_code=? AND order_item_id=? AND status='OCCUPIED'
                """,item.get("sourceId"),item.get("sourceBarcode"),sourceOrderId,now,childOrder.get("store_code"),item.get("childId"));
            jdbc.update("UPDATE order_item SET status='BACK_TO_STORE',shelf_status=1,shelf_code=?,on_shelf_time=?,update_time=? WHERE id=?",
                    item.get("shelfCode"),item.get("onShelfTime"),now,item.get("sourceId"));
            jdbc.update("UPDATE order_item SET status='REWORK_COMPLETED',shelf_status=0,shelf_code=NULL,update_time=? WHERE id=?",now,item.get("childId"));
        }
        jdbc.update("UPDATE laundry_order SET status='REWORK_COMPLETED',update_time=? WHERE id=? AND status='SENT_TO_FACTORY'",now,childId);
        Integer pending=jdbc.queryForObject("SELECT COUNT(*) FROM order_item WHERE order_id=? AND status='STORE_REWORKING'",Integer.class,sourceOrderId);
        if(pending!=null&&pending==0){
            jdbc.update("UPDATE laundry_order SET status='BACK_TO_STORE',update_time=? WHERE id=? AND status='STORE_REWORKING'",now,sourceOrderId);
            Map<String,Object> source=jdbc.queryForMap("SELECT order_no,customer_phone,store_code FROM laundry_order WHERE id=?",sourceOrderId);
            for(String channel:List.of("SMS","MINIAPP"))jdbc.update("""
                INSERT INTO customer_notification(request_id,store_code,order_id,order_no,notification_type,channel,recipient,content,status,last_error,operator_id,operator_name,create_time,update_time)
                VALUES (?,?,?,?,?,?,?,?, 'NOT_CONFIGURED','通知渠道尚未配置',?,?,?,?)
                """,java.util.UUID.randomUUID().toString(),source.get("store_code"),sourceOrderId,source.get("order_no"),"STORE_RETURN_COMPLETE",channel,
                "SMS".equals(channel)?source.get("customer_phone"):null,"返洗衣物已重新回店，可以取衣。",operatorId,operatorName,now,now);
        }
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
