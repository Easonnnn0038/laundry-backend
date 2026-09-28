package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final JdbcTemplate jdbc; private final CurrentUserUtil user;
    public NotificationController(JdbcTemplate jdbc,CurrentUserUtil user){this.jdbc=jdbc;this.user=user;}
    public record SendRequest(@NotBlank String requestId,@NotEmpty List<String> orderNos,@NotEmpty List<String> channels,@NotBlank String type){}
    @GetMapping("/candidates") public Result<List<Map<String,Object>>> candidates(){return Result.success(jdbc.queryForList("""
        SELECT o.order_no AS orderNo,CONCAT(LEFT(o.customer_phone,3),'****',RIGHT(o.customer_phone,4)) AS maskedPhone,o.total_count AS itemCount,o.status,
          CASE WHEN o.status='STORE_REWORKING' THEN 'STORE_RETURN_DELAY' ELSE 'PICKUP_READY' END AS suggestedType,
          (SELECT n.status FROM customer_notification n WHERE n.order_id=o.id ORDER BY n.id DESC LIMIT 1) AS lastStatus
        FROM laundry_order o WHERE o.store_code=? AND o.cancel_flag=0 AND o.status IN ('BACK_TO_STORE','NOTIFIED','PARTIALLY_PICKED_UP','STORE_REWORKING') ORDER BY o.update_time DESC
        """,user.getStoreCode()));}
    @GetMapping("/records") public Result<List<Map<String,Object>>> records(){return Result.success(jdbc.queryForList("""
        SELECT id,batch_no AS batchNo,order_no AS orderNo,notification_type AS notificationType,channel,status,attempt_count AS attemptCount,last_error AS lastError,sent_at AS sentAt,create_time AS createTime
        FROM customer_notification WHERE store_code=? ORDER BY id DESC LIMIT 300
        """,user.getStoreCode()));}
    @PostMapping("/send") @Transactional public Result<Map<String,Object>> send(@Valid @RequestBody SendRequest req){
        String type=req.type().trim().toUpperCase();if(!Set.of("PICKUP_READY","STORE_RETURN_DELAY","STORE_RETURN_COMPLETE","PICKUP_REMINDER").contains(type))throw new IllegalArgumentException("通知类型不正确");
        LinkedHashSet<String> channels=new LinkedHashSet<>();for(String value:req.channels()){String c=value.trim().toUpperCase();if(!Set.of("SMS","MINIAPP").contains(c))throw new IllegalArgumentException("通知渠道不正确");channels.add(c);}
        LinkedHashSet<String> orderNos=new LinkedHashSet<>(req.orderNos());if(orderNos.size()>100)throw new IllegalArgumentException("单次最多通知100个订单");
        String batchNo=orderNos.size()>1?"NT"+LocalDateTime.now().toString().replaceAll("[-:T.]","").substring(0,14):null;int created=0;LocalDateTime now=LocalDateTime.now();
        for(String orderNo:orderNos){List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM laundry_order WHERE order_no=? AND store_code=? AND cancel_flag=0 FOR UPDATE",orderNo,user.getStoreCode());if(rows.size()!=1)throw new IllegalArgumentException("订单不存在："+orderNo);Map<String,Object> o=rows.get(0);String status=String.valueOf(o.get("status"));
            if("STORE_RETURN_DELAY".equals(type)){if(!"STORE_REWORKING".equals(status))throw new IllegalArgumentException("订单 "+orderNo+" 不在店返处理中");}else if(!Set.of("BACK_TO_STORE","NOTIFIED","PARTIALLY_PICKED_UP").contains(status))throw new IllegalArgumentException("订单 "+orderNo+" 当前不能发送取衣通知");
            String content=content(type,o);for(String channel:channels)created+=jdbc.update("""
                INSERT IGNORE INTO customer_notification(request_id,batch_no,store_code,order_id,order_no,notification_type,channel,recipient,content,status,last_error,operator_id,operator_name,create_time,update_time)
                VALUES (?,?,?,?,?,?,?,?,?,'NOT_CONFIGURED','通知供应商尚未配置',?,?,?,?)
                """,req.requestId(),batchNo,user.getStoreCode(),o.get("id"),o.get("order_no"),type,channel,"SMS".equals(channel)?o.get("customer_phone"):null,content,user.getOperatorId(),user.getOperatorName(),now,now);
        }return Result.success(Map.of("created",created,"batchNo",batchNo==null?"":batchNo,"status","NOT_CONFIGURED"));}
    private String content(String type,Map<String,Object> o){return switch(type){case "STORE_RETURN_DELAY"->"您的衣物正在返洗处理，取衣时间可能延后。订单号 "+o.get("order_no");case "STORE_RETURN_COMPLETE"->"返洗衣物已重新回店，可以到店取衣。订单号 "+o.get("order_no");case "PICKUP_REMINDER"->"您的衣物已回店，请及时到店领取。订单号 "+o.get("order_no");default->"衣物已回店，请凭完整手机号或四位取衣码 "+o.get("pickup_code")+" 到店领取。订单号 "+o.get("order_no");};}
}
