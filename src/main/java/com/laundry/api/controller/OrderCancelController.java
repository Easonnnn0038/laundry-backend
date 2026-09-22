package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order-cancel")
public class OrderCancelController {
 private final JdbcTemplate jdbc; private final CurrentUserUtil user;
 public OrderCancelController(JdbcTemplate jdbc,CurrentUserUtil user){this.jdbc=jdbc;this.user=user;}
 public record ApplyRequest(@NotBlank String orderNo,@NotBlank String reason){} public record ReviewRequest(@NotBlank String action,String note){}
 @GetMapping("/search") public Result<List<Map<String,Object>>> search(@RequestParam(required=false) String keyword){
  String term=keyword==null?"":keyword.trim(); boolean admin="ADMIN".equals(user.getUser().getRole());
  return Result.success(jdbc.queryForList("""
    SELECT r.id,r.order_no AS orderNo,o.customer_phone AS phone,o.total_count AS itemCount,o.status AS orderStatus,
      r.reason,r.status,r.applicant_name AS applicantName,r.apply_time AS applyTime,r.reviewer_name AS reviewerName,
      r.review_note AS reviewNote,r.review_time AS reviewTime,r.restore_time AS restoreTime
    FROM order_cancel_request r JOIN laundry_order o ON o.id=r.order_id
    WHERE r.store_code=? AND (?='' OR r.order_no=? OR o.customer_phone=?) AND (?=1 OR r.applicant_id=?)
    ORDER BY r.apply_time DESC LIMIT 200
    """,user.getStoreCode(),term,term,term,admin?1:0,user.getOperatorId()));
 }
 @GetMapping("/eligible") public Result<List<Map<String,Object>>> eligible(@RequestParam String keyword){
  String t=keyword==null?"":keyword.trim(); if(t.length()<4)throw new IllegalArgumentException("请输入完整订单号或手机号");
  return Result.success(jdbc.queryForList("""
   SELECT o.order_no AS orderNo,o.customer_phone AS phone,o.total_count AS itemCount,o.receive_time AS receiveTime
   FROM laundry_order o WHERE o.store_code=? AND o.status='RECEIVED' AND o.cancel_flag=0 AND (o.order_no=? OR o.customer_phone=?)
   AND NOT EXISTS(SELECT 1 FROM order_cancel_request r WHERE r.order_id=o.id AND r.status='PENDING') ORDER BY o.receive_time DESC
   """,user.getStoreCode(),t,t));
 }
 @PostMapping("/apply") @Transactional public Result<Map<String,Object>> apply(@Valid @RequestBody ApplyRequest req){
  if(!"EMPLOYEE".equals(user.getUser().getRole()))throw new IllegalArgumentException("管理员请审核店员申请，无需代为提交");
  String reason=req.reason().trim();if(reason.length()>500)throw new IllegalArgumentException("申请原因不能超过500字");
  List<Map<String,Object>> os=jdbc.queryForList("SELECT * FROM laundry_order WHERE order_no=? AND store_code=? FOR UPDATE",req.orderNo().trim(),user.getStoreCode());
  if(os.size()!=1||!"RECEIVED".equals(os.get(0).get("status"))||((Number)os.get(0).get("cancel_flag")).intValue()!=0)throw new IllegalArgumentException("仅未送厂订单可以申请取消");
  Map<String,Object> o=os.get(0);Integer pending=jdbc.queryForObject("SELECT COUNT(*) FROM order_cancel_request WHERE order_id=? AND status='PENDING'",Integer.class,o.get("id"));if(pending!=null&&pending>0)throw new IllegalArgumentException("该订单已有待审核申请");
  LocalDateTime now=LocalDateTime.now();jdbc.update("INSERT INTO order_cancel_request(order_id,order_no,store_code,reason,applicant_id,applicant_name,apply_time) VALUES (?,?,?,?,?,?,?)",o.get("id"),o.get("order_no"),user.getStoreCode(),reason,user.getOperatorId(),user.getOperatorName(),now);
  return Result.success(Map.of("orderNo",o.get("order_no"),"status","PENDING"));
 }
 @PostMapping("/{id}/review") @Transactional public Result<Map<String,Object>> review(@PathVariable long id,@Valid @RequestBody ReviewRequest req){
  admin();String action=req.action().trim().toUpperCase();if(!List.of("APPROVE","REJECT").contains(action))throw new IllegalArgumentException("无效审核操作");
  List<Map<String,Object>> rs=jdbc.queryForList("SELECT * FROM order_cancel_request WHERE id=? AND store_code=? FOR UPDATE",id,user.getStoreCode());if(rs.size()!=1||!"PENDING".equals(rs.get(0).get("status")))throw new IllegalArgumentException("申请不存在或已审核");Map<String,Object> r=rs.get(0);LocalDateTime now=LocalDateTime.now();
  String result="APPROVE".equals(action)?"APPROVED":"REJECTED";
  if("APPROVED".equals(result)){int changed=jdbc.update("UPDATE laundry_order SET status='CANCELLED',cancel_flag=1,cancel_time=?,cancel_operator=?,cancel_reason=?,update_time=? WHERE id=? AND status='RECEIVED' AND cancel_flag=0",now,user.getOperatorName(),r.get("reason"),now,r.get("order_id"));if(changed!=1)throw new IllegalArgumentException("订单已送厂或状态已变化，不能批准");jdbc.update("UPDATE order_item SET status='CANCELLED',update_time=? WHERE order_id=?",now,r.get("order_id"));jdbc.update("""
    INSERT INTO order_operate_log(order_id,order_no,operate_type,operate_desc,before_status,after_status,operator_id,operator_name,operate_time,remark,create_time)
    VALUES (?,?,'CANCEL','管理员批准店员的取消申请','RECEIVED','CANCELLED',?,?,?,?,?)
    """,r.get("order_id"),r.get("order_no"),user.getOperatorId(),user.getOperatorName(),now,r.get("reason"),now);}
  jdbc.update("UPDATE order_cancel_request SET status=?,reviewer_id=?,reviewer_name=?,review_note=?,review_time=? WHERE id=?",result,user.getOperatorId(),user.getOperatorName(),req.note(),now,id);return Result.success(Map.of("id",id,"status",result));
 }
 @PostMapping("/{id}/restore") @Transactional public Result<Map<String,Object>> restore(@PathVariable long id){
  admin();List<Map<String,Object>> rs=jdbc.queryForList("SELECT * FROM order_cancel_request WHERE id=? AND store_code=? FOR UPDATE",id,user.getStoreCode());if(rs.size()!=1||!"APPROVED".equals(rs.get(0).get("status")))throw new IllegalArgumentException("仅已批准且未恢复的取消单可恢复");Map<String,Object> r=rs.get(0);
  Integer packages=jdbc.queryForObject("SELECT COUNT(*) FROM factory_package WHERE order_id=?",Integer.class,r.get("order_id"));if(packages!=null&&packages>0)throw new IllegalArgumentException("订单已有物流记录，不能恢复");LocalDateTime now=LocalDateTime.now();
  int changed=jdbc.update("UPDATE laundry_order SET status='RECEIVED',cancel_flag=0,cancel_time=NULL,cancel_operator=NULL,cancel_reason=NULL,update_time=? WHERE id=? AND status='CANCELLED'",now,r.get("order_id"));if(changed!=1)throw new IllegalArgumentException("订单状态不允许恢复");jdbc.update("UPDATE order_item SET status='RECEIVED',update_time=? WHERE order_id=?",now,r.get("order_id"));jdbc.update("UPDATE order_cancel_request SET status='RESTORED',restore_time=? WHERE id=?",now,id);jdbc.update("""
    INSERT INTO order_operate_log(order_id,order_no,operate_type,operate_desc,before_status,after_status,operator_id,operator_name,operate_time,create_time)
    VALUES (?,?,'RESTORE','管理员恢复误取消订单','CANCELLED','RECEIVED',?,?,?,?)
    """,r.get("order_id"),r.get("order_no"),user.getOperatorId(),user.getOperatorName(),now,now);return Result.success(Map.of("id",id,"status","RESTORED"));
 }
 private void admin(){if(!"ADMIN".equals(user.getUser().getRole()))throw new AccessDeniedException("仅管理员可审核和恢复订单");}
}
