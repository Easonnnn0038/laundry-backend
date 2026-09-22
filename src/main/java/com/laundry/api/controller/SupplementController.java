package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/supplements")
public class SupplementController {
    private final JdbcTemplate jdbc;
    private final CurrentUserUtil user;
    public SupplementController(JdbcTemplate jdbc, CurrentUserUtil user) { this.jdbc=jdbc; this.user=user; }
    public record CreateRequest(@NotBlank String orderNo,@NotNull Long parentItemId,
                                @NotBlank String attachmentName,@PositiveOrZero BigDecimal feeAmount,String remark) {}

    @GetMapping("/orders")
    public Result<List<Map<String,Object>>> orders(@RequestParam String keyword) {
        String term=keyword==null?"":keyword.trim();
        if(term.length()<4) throw new IllegalArgumentException("请输入完整订单号或手机号");
        return Result.success(jdbc.queryForList("""
            SELECT o.id,o.order_no AS orderNo,o.customer_phone AS phone,o.status,o.total_count AS totalCount,
                   o.receive_time AS receiveTime FROM laundry_order o
            WHERE o.store_code=? AND o.cancel_flag=0 AND o.status IN ('RECEIVED','SENT_TO_FACTORY')
              AND (o.order_no=? OR o.customer_phone=?) ORDER BY o.receive_time DESC
            """,user.getStoreCode(),term,term));
    }

    @GetMapping("/orders/{orderNo}")
    public Result<Map<String,Object>> detail(@PathVariable String orderNo) {
        Map<String,Object> order=order(orderNo,false);
        long id=((Number)order.get("id")).longValue();
        order.put("items",jdbc.queryForList("""
            SELECT id,barcode,category_name AS categoryName,color,brand,item_kind AS itemKind
            FROM order_item WHERE order_id=? ORDER BY item_seq
            """,id));
        order.put("supplements",jdbc.queryForList("""
            SELECT id,attachment_name AS attachmentName,fee_amount AS feeAmount,payment_status AS paymentStatus,
                   dispatch_status AS dispatchStatus,package_no AS packageNo,remark,create_time AS createTime
            FROM supplement_attachment WHERE order_id=? ORDER BY id DESC
            """,id));
        return Result.success(order);
    }

    @PostMapping
    @Transactional(rollbackFor=Exception.class)
    public Result<Map<String,Object>> create(@Valid @RequestBody CreateRequest req) {
        Map<String,Object> order=order(req.orderNo(),true);
        String status=String.valueOf(order.get("status"));
        if(!List.of("RECEIVED","SENT_TO_FACTORY").contains(status)) throw new IllegalArgumentException("订单当前状态不能补收附件");
        long orderId=((Number)order.get("id")).longValue();
        List<Map<String,Object>> parents=jdbc.queryForList("""
            SELECT * FROM order_item WHERE id=? AND order_id=? AND item_kind='GARMENT'
            """,req.parentItemId(),orderId);
        if(parents.size()!=1) throw new IllegalArgumentException("请选择该订单中的原衣物");
        String name=req.attachmentName().trim();
        if(name.length()>100) throw new IllegalArgumentException("附件名称不能超过100字");
        BigDecimal fee=req.feeAmount()==null?BigDecimal.ZERO:req.feeAmount();
        Integer seq=jdbc.queryForObject("SELECT COALESCE(MAX(item_seq),0)+1 FROM order_item WHERE order_id=?",Integer.class,orderId);
        if(seq==null||seq>99) throw new IllegalArgumentException("该订单衣物及附件已达到99件上限");
        String prefix=jdbc.queryForObject("SELECT LEFT(barcode,10) FROM order_item WHERE order_id=? ORDER BY item_seq LIMIT 1",String.class,orderId);
        String barcode=prefix+String.format("%02d",seq);
        Map<String,Object> parent=parents.get(0); LocalDateTime now=LocalDateTime.now();
        KeyHolder itemKey=new GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement ps=c.prepareStatement("""
            INSERT INTO order_item(order_id,order_no,item_seq,barcode,category_id,category_group,category_name,
              quantity,unit_price,subtotal,color,brand,special,shelf_status,error_back_flag,status,
              parent_item_id,item_kind,create_time,update_time)
            VALUES (?,?,?,?,?,'ACCESSORY',?,1,?,?,NULL,NULL,?,0,0,?,?, 'ACCESSORY',?,?)
            """,Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,orderId);ps.setString(2,String.valueOf(order.get("order_no")));ps.setInt(3,seq);ps.setString(4,barcode);
            ps.setObject(5,parent.get("category_id"));ps.setString(6,"附件："+name);ps.setBigDecimal(7,fee);ps.setBigDecimal(8,fee);
            ps.setString(9,req.remark());ps.setString(10,status);ps.setLong(11,req.parentItemId());ps.setObject(12,now);ps.setObject(13,now);return ps;
        },itemKey);
        long itemId=itemKey.getKey().longValue();
        String dispatch="RECEIVED".equals(status)?"MERGED":"PENDING"; Long packageId=null; String packageNo=null;
        if("PENDING".equals(dispatch)) {
            packageNo="PKA"+user.getStoreCode()+now.format(DateTimeFormatter.ofPattern("MMddHHmmss"))+UUID.randomUUID().toString().substring(0,3).toUpperCase();
            KeyHolder packageKey=new GeneratedKeyHolder(); String finalPackageNo=packageNo;
            jdbc.update(c->{PreparedStatement ps=c.prepareStatement("""
                INSERT INTO factory_package(package_no,order_id,order_no,package_seq,expected_item_count,
                  received_item_count,status,create_time,update_time) VALUES (?,?,?,?,1,0,'WAIT_DISPATCH',?,?)
                """,Statement.RETURN_GENERATED_KEYS);ps.setString(1,finalPackageNo);ps.setLong(2,orderId);ps.setString(3,String.valueOf(order.get("order_no")));
                ps.setInt(4,seq);ps.setObject(5,now);ps.setObject(6,now);return ps;},packageKey);
            packageId=packageKey.getKey().longValue();
            jdbc.update("INSERT INTO factory_package_item(package_id,package_no,order_item_id,barcode,scan_status) VALUES (?,?,?,?,'WAIT_SCAN')",packageId,packageNo,itemId,barcode);
        }
        jdbc.update("UPDATE laundry_order SET total_count=total_count+1,update_time=? WHERE id=?",now,orderId);
        jdbc.update("""
          INSERT INTO supplement_attachment(order_id,order_no,parent_item_id,order_item_id,attachment_name,
            fee_amount,payment_status,dispatch_status,package_id,package_no,remark,operator_id,operator_name,create_time)
          VALUES (?,?,?,?,?,?,'PENDING',?,?,?,?,?,?,?)
          """,orderId,order.get("order_no"),req.parentItemId(),itemId,name,fee,dispatch,packageId,packageNo,req.remark(),user.getOperatorId(),user.getOperatorName(),now);
        jdbc.update("""
          INSERT INTO order_operate_log(order_id,order_no,item_id,barcode,operate_type,operate_desc,before_status,after_status,
            operator_id,operator_name,operate_time,remark,create_time) VALUES (?,?,?,?,'SUPPLEMENT',?,?,?,?,?,?,?,?)
          """,orderId,order.get("order_no"),itemId,barcode,"补收附件："+name,status,status,user.getOperatorId(),user.getOperatorName(),now,req.remark(),now);
        return Result.success(Map.of("barcode",barcode,"dispatchStatus",dispatch,"packageNo",packageNo==null?"":packageNo));
    }

    @PostMapping("/{id}/dispatch")
    @Transactional(rollbackFor=Exception.class)
    public Result<Map<String,Object>> dispatch(@PathVariable long id) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM supplement_attachment WHERE id=? FOR UPDATE",id);
        if(rows.size()!=1) throw new IllegalArgumentException("补收记录不存在"); Map<String,Object> s=rows.get(0);
        if(!user.getStoreCode().equals(String.valueOf(jdbc.queryForObject("SELECT store_code FROM laundry_order WHERE id=?",String.class,s.get("order_id"))))) throw new IllegalArgumentException("无权操作其他门店附件");
        if(!"PENDING".equals(s.get("dispatch_status"))) throw new IllegalArgumentException("附件已并入原单或已经送厂");
        LocalDateTime now=LocalDateTime.now(); String batchNo="PC"+user.getStoreCode()+now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))+"A";
        KeyHolder key=new GeneratedKeyHolder();
        jdbc.update(c->{PreparedStatement ps=c.prepareStatement("""
          INSERT INTO factory_batch(batch_no,store_code,order_count,item_count,send_operator,send_time,status,remark,create_time,update_time)
          VALUES (?,?,1,1,?,?,1,'补收附件单独送厂',?,?)
          """,Statement.RETURN_GENERATED_KEYS);ps.setString(1,batchNo);ps.setString(2,user.getStoreCode());ps.setString(3,user.getOperatorName());ps.setObject(4,now);ps.setObject(5,now);ps.setObject(6,now);return ps;},key);
        long batchId=key.getKey().longValue();
        jdbc.update("UPDATE factory_package SET source_batch_id=?,source_batch_no=?,status='WAIT_FACTORY',update_time=? WHERE id=? AND status='WAIT_DISPATCH'",batchId,batchNo,now,s.get("package_id"));
        jdbc.update("UPDATE order_item SET batch_id=?,batch_no=?,send_factory_time=?,status='SENT_TO_FACTORY',update_time=? WHERE id=?",batchId,batchNo,now,now,s.get("order_item_id"));
        jdbc.update("UPDATE supplement_attachment SET dispatch_status='DISPATCHED',dispatch_time=? WHERE id=?",now,id);
        return Result.success(Map.of("batchNo",batchNo,"packageNo",s.get("package_no")));
    }

    private Map<String,Object> order(String no,boolean lock) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM laundry_order WHERE order_no=? AND store_code=? AND cancel_flag=0"+(lock?" FOR UPDATE":""),no.trim(),user.getStoreCode());
        if(rows.size()!=1) throw new IllegalArgumentException("订单不存在或不属于当前门店"); return rows.get(0);
    }
}
