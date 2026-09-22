package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/store-operations")
public class StoreOperationsController {
    private final JdbcTemplate jdbc;
    private final CurrentUserUtil user;
    public StoreOperationsController(JdbcTemplate jdbc, CurrentUserUtil user) { this.jdbc = jdbc; this.user = user; }

    public record NotifyRequest(@NotBlank String orderNo, @NotBlank String channel) {}
    public record ErrorRequest(@NotBlank String packageNo, @NotBlank String type, @NotBlank String description) {}
    public record ResolveRequest(@NotBlank String action, @NotBlank String note) {}

    @GetMapping("/notifications")
    public Result<List<Map<String, Object>>> notifications() {
        return Result.success(jdbc.queryForList("""
            SELECT o.order_no AS orderNo, o.customer_phone AS phone, o.pickup_code AS pickupCode,
                   o.total_count AS itemCount, o.status,
                   (SELECT COUNT(*) FROM pickup_notification n WHERE n.order_id=o.id) AS notifyCount,
                   (SELECT MAX(n.notified_at) FROM pickup_notification n WHERE n.order_id=o.id) AS lastNotifiedAt
            FROM laundry_order o WHERE o.store_code=? AND o.cancel_flag=0
              AND o.pickup_code IS NOT NULL AND o.status IN ('BACK_TO_STORE','NOTIFIED')
            ORDER BY o.update_time DESC
            """, user.getStoreCode()));
    }

    @PostMapping("/notifications")
    @Transactional
    public Result<Map<String, Object>> notifyCustomer(@Valid @RequestBody NotifyRequest request) {
        String channel = request.channel().trim().toUpperCase();
        if (!List.of("PHONE", "WECHAT").contains(channel)) throw new IllegalArgumentException("通知方式仅支持电话或微信");
        List<Map<String, Object>> orders = jdbc.queryForList("""
            SELECT id, order_no, pickup_code, status FROM laundry_order
            WHERE order_no=? AND store_code=? AND cancel_flag=0 FOR UPDATE
            """, request.orderNo().trim(), user.getStoreCode());
        if (orders.size()!=1 || !List.of("BACK_TO_STORE", "NOTIFIED").contains(orders.get(0).get("status"))
                || orders.get(0).get("pickup_code")==null)
            throw new IllegalArgumentException("订单尚未完整回店，无法通知取衣");
        Map<String, Object> order = orders.get(0);
        String content = "衣物已回店，请凭完整手机号和四位取衣码 " + order.get("pickup_code") + " 到店领取。订单号 " + order.get("order_no");
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
            INSERT INTO pickup_notification(order_id,channel,content,operator_id,operator_name,notified_at)
            VALUES (?,?,?,?,?,?)
            """, order.get("id"), channel, content, user.getOperatorId(), user.getOperatorName(), now);
        jdbc.update("UPDATE laundry_order SET status='NOTIFIED',update_time=? WHERE id=?", now, order.get("id"));
        return Result.success(Map.of("content", content, "channel", channel, "notifiedAt", now));
    }

    @GetMapping("/clothes")
    public Result<List<Map<String, Object>>> clothes(@RequestParam String keyword) {
        String term = keyword == null ? "" : keyword.trim();
        if (term.length()<3 || term.length()>30) throw new IllegalArgumentException("请输入至少3位订单号、衣物码或手机号");
        return Result.success(jdbc.queryForList("""
            SELECT o.order_no AS orderNo, o.customer_phone AS phone, o.status AS orderStatus,
                   o.receive_time AS receiveTime, o.pickup_time AS pickupTime,
                   i.barcode, i.category_name AS categoryName, i.color, i.brand,
                   i.status AS itemStatus, i.shelf_code AS shelfCode, i.error_back_flag AS errorBackFlag,
                   p.package_no AS packageNo, i.batch_no AS sourceBatchNo,
                   s.current_process AS factoryProcess
            FROM laundry_order o JOIN order_item i ON i.order_id=o.id
            LEFT JOIN factory_package_item pi ON pi.order_item_id=i.id
            LEFT JOIN factory_package p ON p.id=pi.package_id
            LEFT JOIN factory_item_state s ON s.order_item_id=i.id
            WHERE o.store_code=? AND (o.customer_phone=? OR o.order_no=? OR i.barcode=?)
            ORDER BY o.receive_time DESC,i.item_seq
            """, user.getStoreCode(), term, term, term));
    }

    private void requireAdmin() {
        if (!"ADMIN".equals(user.getUser().getRole())) throw new org.springframework.security.access.AccessDeniedException("仅管理员可处理错误回店");
    }

    @GetMapping("/return-errors")
    public Result<List<Map<String, Object>>> returnErrors() {
        requireAdmin();
        return Result.success(jdbc.queryForList("""
            SELECT e.id, e.store_code AS storeCode,e.package_no AS packageNo,e.order_no AS orderNo,
                   e.type,e.description,e.status,e.resolution_note AS resolutionNote,
                   e.created_at AS createdAt,e.resolved_at AS resolvedAt
            FROM store_return_error e WHERE e.store_code=? ORDER BY e.created_at DESC
            """, user.getStoreCode()));
    }

    @PostMapping("/return-errors")
    @Transactional
    public Result<Map<String, Object>> createError(@Valid @RequestBody ErrorRequest request) {
        requireAdmin();
        String type = request.type().trim().toUpperCase();
        if (!List.of("MISSING","WRONG_ITEM","WRONG_STORE","OTHER").contains(type)) throw new IllegalArgumentException("无效异常类型");
        String description = request.description().trim();
        if (description.isEmpty() || description.length()>500) throw new IllegalArgumentException("异常说明需为1至500字");
        String packageNo = request.packageNo().trim();
        List<Map<String,Object>> found = jdbc.queryForList("""
            SELECT fp.id, fp.order_no, fp.status AS packageStatus, o.store_code AS expectedStore,
                   rbp.return_batch_id AS batchId,rbp.store_receive_status AS receiveStatus
            FROM factory_package fp JOIN laundry_order o ON o.id=fp.order_id
            LEFT JOIN factory_return_batch_package rbp ON rbp.package_id=fp.id
            WHERE fp.package_no=? ORDER BY rbp.id DESC LIMIT 1
            """, packageNo);
        if (found.isEmpty()) throw new IllegalArgumentException("大件码不存在，请先核对");
        Map<String,Object> pkg = found.get(0);
        if (pkg.get("batchId")==null || !List.of("WAIT_SCAN", "EXCEPTION").contains(String.valueOf(pkg.get("receiveStatus"))))
            throw new IllegalArgumentException("大件尚未发回门店，或已完成签收");
        boolean wrongStore = !user.getStoreCode().equals(pkg.get("expectedStore"));
        if (wrongStore != "WRONG_STORE".equals(type)) throw new IllegalArgumentException(wrongStore ? "该大件属于其他门店，请选错店" : "该大件属于本店，请选择其他异常类型");
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM store_return_error WHERE store_code=? AND package_no=? AND status='OPEN'", Integer.class, user.getStoreCode(),packageNo);
        if (open!=null && open>0) throw new IllegalArgumentException("该大件已有待处理异常");
        if (!wrongStore && "WAIT_SCAN".equals(pkg.get("receiveStatus"))) {
            jdbc.update("""
                UPDATE factory_return_batch_package SET store_receive_status='EXCEPTION',exception_reason=?,
                    exception_time=?,exception_operator_id=? WHERE package_id=? AND store_receive_status='WAIT_SCAN'
                """, description,LocalDateTime.now(),user.getOperatorId(),pkg.get("id"));
            jdbc.update("UPDATE factory_package SET status='FROZEN',frozen_reason=?,update_time=? WHERE id=?", description,LocalDateTime.now(),pkg.get("id"));
        }
        if (!wrongStore && "WRONG_ITEM".equals(type)) {
            jdbc.update("""
                UPDATE order_item oi JOIN factory_package_item fpi ON fpi.order_item_id=oi.id
                SET oi.error_back_flag=1,oi.error_back_remark=?,oi.error_back_time=?
                WHERE fpi.package_id=?
                """,description,LocalDateTime.now(),pkg.get("id"));
        }
        jdbc.update("""
            INSERT INTO store_return_error(store_code,package_no,order_no,return_batch_id,type,description,
                created_by,created_at) VALUES (?,?,?,?,?,?,?,?)
            """,user.getStoreCode(),packageNo,pkg.get("order_no"),pkg.get("batchId"),type,description,user.getOperatorId(),LocalDateTime.now());
        return Result.success(Map.of("packageNo",packageNo,"status","OPEN"));
    }

    @PostMapping("/return-errors/{id}/resolve")
    @Transactional
    public Result<Map<String,Object>> resolveError(@PathVariable long id,@Valid @RequestBody ResolveRequest request) {
        requireAdmin();
        String action=request.action().trim().toUpperCase();
        if (!List.of("RETURNED","RECHECK").contains(action)) throw new IllegalArgumentException("处理方式应为退回工厂或重新核对");
        String note=request.note().trim();
        if (note.isEmpty() || note.length()>500) throw new IllegalArgumentException("处理说明需为1至500字");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM store_return_error WHERE id=? AND store_code=? FOR UPDATE",id,user.getStoreCode());
        if (rows.size()!=1 || !"OPEN".equals(rows.get(0).get("status"))) throw new IllegalArgumentException("异常不存在或已处理");
        Map<String,Object> error=rows.get(0);
        if ("RECHECK".equals(action)) {
            if ("WRONG_STORE".equals(error.get("type"))) throw new IllegalArgumentException("错店大件不能在本店重新签收");
            int changed=jdbc.update("""
                UPDATE factory_return_batch_package SET store_receive_status='WAIT_SCAN',exception_reason=NULL,
                    exception_time=NULL,exception_operator_id=NULL
                WHERE return_batch_id=? AND package_no=? AND store_code=? AND store_receive_status='EXCEPTION'
                """,error.get("return_batch_id"),error.get("package_no"),user.getStoreCode());
            if (changed!=1) throw new IllegalArgumentException("大件当前无法重新核对");
            jdbc.update("DELETE FROM factory_store_receive_scan WHERE package_id=(SELECT id FROM factory_package WHERE package_no=?)",error.get("package_no"));
            jdbc.update("UPDATE factory_package SET status='RETURNING',frozen_reason=NULL,update_time=? WHERE package_no=?",LocalDateTime.now(),error.get("package_no"));
            jdbc.update("""
                UPDATE order_item oi JOIN factory_package_item fpi ON fpi.order_item_id=oi.id
                JOIN factory_package fp ON fp.id=fpi.package_id
                SET oi.error_back_flag=0,oi.error_back_remark=NULL,oi.error_back_time=NULL
                WHERE fp.package_no=?
                """,error.get("package_no"));
        }
        jdbc.update("UPDATE store_return_error SET status=?,resolution_note=?,resolved_by=?,resolved_at=? WHERE id=?",
            action,note,user.getOperatorId(),LocalDateTime.now(),id);
        return Result.success(Map.of("id",id,"status",action));
    }
}
