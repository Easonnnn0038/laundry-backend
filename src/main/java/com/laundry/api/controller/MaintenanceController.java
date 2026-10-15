package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.service.MaintenanceLogService;
import com.laundry.api.service.MiniappService;
import com.laundry.api.service.PersonalDataCleanupService;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {
    private final JdbcTemplate jdbc; private final CurrentUserUtil user; private final MaintenanceLogService logs; private final MiniappService miniapp; private final PersonalDataCleanupService cleanup;
    public MaintenanceController(JdbcTemplate jdbc,CurrentUserUtil user,MaintenanceLogService logs,MiniappService miniapp,PersonalDataCleanupService cleanup){this.jdbc=jdbc;this.user=user;this.logs=logs;this.miniapp=miniapp;this.cleanup=cleanup;}
    @Value("${maintenance.factory-host:127.0.0.1}") private String factoryHost;
    @Value("${maintenance.factory-port:8081}") private int factoryPort;
    @Value("${spring.rabbitmq.host:127.0.0.1}") private String rabbitHost;
    @Value("${spring.rabbitmq.port:5672}") private int rabbitPort;
    public record ResolveRequest(@NotBlank String status,String note){}
    public record ClientErrorRequest(String module,@NotBlank String message,String path,String clientVersion){}
    public record DeletionReviewRequest(boolean identityVerified,@NotBlank @Size(max=500) String note){}

    @GetMapping("/status") public Result<Map<String,Object>> status(){admin();
        Map<String,Object> result=new LinkedHashMap<>();result.put("backend",check("门店后端",true,"1.0.0"));
        boolean db;try{db=jdbc.queryForObject("SELECT 1",Integer.class)==1;}catch(Exception e){db=false;}result.put("database",check("MySQL 数据库",db,db?"连接正常":"连接失败"));
        result.put("factory",check("工厂后端",tcp(factoryHost,factoryPort),factoryHost+":"+factoryPort));
        result.put("rabbitmq",checkOptional("RabbitMQ",tcp(rabbitHost,rabbitPort),rabbitHost+":"+rabbitPort));
        result.put("startedAt",LocalDateTime.now().minusSeconds(ManagementFactory.getRuntimeMXBean().getUptime()/1000));
        result.put("uptimeSeconds",ManagementFactory.getRuntimeMXBean().getUptime()/1000);result.put("checkedAt",LocalDateTime.now());return Result.success(result);
    }

    @GetMapping("/events") public Result<List<Map<String,Object>>> events(@RequestParam(required=false) String category,@RequestParam(required=false) String status,
        @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){admin();validateDates(from,to);return Result.success(findEvents(category,status,from,to));}

    @PostMapping("/events/{id}/status") public Result<Map<String,Object>> resolve(@PathVariable long id,@Valid @RequestBody ResolveRequest req){admin();
        String status=req.status().trim().toUpperCase();if(!List.of("OPEN","PROCESSING","RESOLVED","IGNORED").contains(status))throw new IllegalArgumentException("无效处理状态");
        String note=req.note()==null?null:req.note().trim();if(("RESOLVED".equals(status)||"IGNORED".equals(status))&&(note==null||note.isEmpty()))throw new IllegalArgumentException("解决或忽略时必须填写处理说明");
        int changed=jdbc.update("UPDATE system_event_log SET status=?,resolution_note=?,resolver_name=?,resolved_time=? WHERE id=?",status,note,user.getOperatorName(),List.of("RESOLVED","IGNORED").contains(status)?LocalDateTime.now():null,id);
        if(changed!=1)throw new IllegalArgumentException("日志不存在");return Result.success(Map.of("id",id,"status",status));}

    @GetMapping("/audit") public Result<List<Map<String,Object>>> audit(@RequestParam(required=false) String keyword,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){admin();
        String term=keyword==null?"":keyword.trim();LocalDate start=from==null?LocalDate.now().minusDays(30):from;LocalDate end=to==null?LocalDate.now():to;
        validateDates(start,end);
        return Result.success(jdbc.queryForList("""
          SELECT l.id,l.order_no AS orderNo,l.operate_type AS operateType,l.operate_desc AS description,
            l.before_status AS beforeStatus,l.after_status AS afterStatus,l.operator_name AS operatorName,
            l.operate_time AS operateTime,l.remark FROM order_operate_log l JOIN laundry_order o ON o.id=l.order_id
          WHERE o.store_code=? AND l.operate_time>=? AND l.operate_time<? AND (?='' OR l.order_no=? OR l.operator_name LIKE CONCAT('%',?,'%'))
          ORDER BY l.operate_time DESC LIMIT 500
          """,user.getStoreCode(),start.atStartOfDay(),end.plusDays(1).atStartOfDay(),term,term,term));}

    @GetMapping("/miniapp/deletion-requests") public Result<List<Map<String,Object>>> miniappDeletionRequests(){admin();
        return Result.success(jdbc.queryForList("""
          SELECT id,phone,status,request_time AS requestTime,reviewed_by AS reviewedBy,
            review_note AS reviewNote,reviewed_time AS reviewedTime,
            (SELECT DATE_ADD(MIN(CASE WHEN o.status='PICKED_UP' THEN o.pickup_time
                WHEN o.status='DELIVERED' THEN o.delivered_time ELSE o.cancel_time END),INTERVAL 6 MONTH)
             FROM laundry_order o WHERE o.customer_phone=r.phone AND o.status IN ('PICKED_UP','DELIVERED','CANCELLED')) AS formalReviewAt,
            (SELECT DATE_ADD(MIN(CASE WHEN p.status='CANCELLED' THEN p.update_time
               WHEN o.status='PICKED_UP' THEN o.pickup_time
               WHEN o.status='DELIVERED' THEN o.delivered_time ELSE o.cancel_time END),INTERVAL 6 MONTH)
             FROM pickup_order p LEFT JOIN laundry_order o ON o.id=p.formal_order_id
             WHERE p.customer_phone=r.phone AND p.status IN ('CANCELLED','CONVERTED')) AS pickupReviewAt
          FROM miniapp_deletion_request r ORDER BY status='PENDING' DESC,request_time ASC LIMIT 200
          """));}

    @PostMapping("/miniapp/deletion-requests/{id}/account-closure") @Transactional
    public Result<Map<String,Object>> closeMiniappAccount(@PathVariable long id,@Valid @RequestBody DeletionReviewRequest req){admin();
        if(!req.identityVerified())throw new IllegalArgumentException("须先核验申请人身份");
        if(req.note().matches("(?s).*1[3-9]\\d{9}.*"))throw new IllegalArgumentException("处理说明请勿填写完整手机号");
        List<String> applicants=jdbc.query("SELECT openid FROM miniapp_deletion_request WHERE id=?",(rs,n)->rs.getString(1),id);
        if(applicants.isEmpty())throw new IllegalArgumentException("删除申请不存在");
        String openid=applicants.get(0);
        // 与客户自行注销保持同一锁顺序：先用户行，后申请行。
        List<Map<String,Object>> accounts=jdbc.queryForList("SELECT id FROM miniapp_user WHERE openid=? FOR UPDATE",openid);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT openid,status FROM miniapp_deletion_request WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty())throw new IllegalArgumentException("删除申请不存在");
        Map<String,Object> row=rows.get(0);
        if(!"PENDING".equals(row.get("status")))throw new IllegalArgumentException("该申请已处理，请刷新列表");
        if(!accounts.isEmpty()){
            Map<String,Object> outcome=miniapp.closeAccount(openid);
            if("PENDING_SERVICE".equals(outcome.get("status")))return Result.success(outcome);
        }
        int changed=jdbc.update("""
          UPDATE miniapp_deletion_request SET status='ACCOUNT_CLOSED',reviewed_by=?,review_note=?,reviewed_time=NOW()
          WHERE id=? AND status='PENDING'
          """,user.getOperatorName(),req.note().trim(),id);
        if(changed!=1)throw new IllegalArgumentException("申请状态已变化，请刷新后重试");
        return Result.success(Map.of("status","ACCOUNT_CLOSED","message","账户与常用地址已按规则处理；订单及财务资料仍按保留期限复核"));}

    @PostMapping("/miniapp/deletion-requests/{id}/clear-due")
    public Result<Map<String,Object>> clearDuePersonalData(@PathVariable long id){admin();return Result.success(cleanup.clearDue(id,user.getOperatorName()));}

    @GetMapping("/events/export") public ResponseEntity<byte[]> export(@RequestParam(required=false) String category,@RequestParam(required=false) String status,
        @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){admin();validateDates(from,to);List<Map<String,Object>> rows=findEvents(category,status,from,to);
        StringBuilder csv=new StringBuilder("\uFEFF编号,分类,级别,模块,信息,路径,操作员,门店,次数,首次时间,最近时间,状态,处理说明\r\n");
        for(Map<String,Object> r:rows)csv.append(cell(r.get("id"))).append(',').append(cell(r.get("category"))).append(',').append(cell(r.get("level"))).append(',').append(cell(r.get("module"))).append(',').append(cell(r.get("message"))).append(',').append(cell(r.get("requestPath"))).append(',').append(cell(r.get("operatorUsername"))).append(',').append(cell(r.get("storeCode"))).append(',').append(cell(r.get("occurrenceCount"))).append(',').append(cell(r.get("firstTime"))).append(',').append(cell(r.get("lastTime"))).append(',').append(cell(r.get("status"))).append(',').append(cell(r.get("resolutionNote"))).append("\r\n");
        HttpHeaders h=new HttpHeaders();h.setContentType(new MediaType("text","csv",StandardCharsets.UTF_8));h.setContentDisposition(ContentDisposition.attachment().filename("maintenance-events-"+LocalDate.now()+".csv").build());return ResponseEntity.ok().headers(h).body(csv.toString().getBytes(StandardCharsets.UTF_8));}

    @PostMapping("/client-error") public Result<Void> clientError(@Valid @RequestBody ClientErrorRequest req){logs.recordClient(req.module()==null?"门店前端":req.module(),req.message(),req.path(),req.clientVersion());return Result.success();}

    @GetMapping("/mq/outbox") public Result<List<Map<String,Object>>> outbox(@RequestParam(required=false) String status){admin();
        String normalized=status==null?"":status.trim().toUpperCase();if(!normalized.isEmpty()&&!List.of("PENDING","PUBLISHING","RETRY","SENT","DEAD").contains(normalized))throw new IllegalArgumentException("无效消息状态");
        return Result.success(jdbc.queryForList("""
          SELECT o.id,o.event_id AS eventId,o.event_type AS eventType,o.aggregate_id AS aggregateId,
            o.status,o.attempt_count AS attemptCount,o.next_attempt_time AS nextAttemptTime,
            o.sent_time AS sentTime,o.last_error AS lastError,o.create_time AS createTime,
            c.status AS consumeStatus,c.processed_time AS processedTime,c.last_error AS consumeError
          FROM mq_outbox o LEFT JOIN mq_consumed_event c
            ON c.consumer_name='PRINT_TASK_CONSUMER' AND c.event_id=o.event_id
          WHERE o.store_code=? AND (?='' OR o.status=?) ORDER BY o.id DESC LIMIT 200
          """,user.getStoreCode(),normalized,normalized));}

    @PostMapping("/mq/outbox/{id}/retry") @Transactional
    public Result<Map<String,Object>> retryMessage(@PathVariable long id){admin();
        List<Map<String,Object>> rows=jdbc.queryForList("""
          SELECT o.event_id,o.status,c.status AS consume_status FROM mq_outbox o
          LEFT JOIN mq_consumed_event c ON c.consumer_name='PRINT_TASK_CONSUMER' AND c.event_id=o.event_id
          WHERE o.id=? AND o.store_code=? FOR UPDATE
          """,id,user.getStoreCode());
        if(rows.size()!=1)throw new IllegalArgumentException("消息不存在");Map<String,Object> row=rows.get(0);
        if(!"DEAD".equals(row.get("status"))&&!"DEAD".equals(row.get("consume_status")))throw new IllegalArgumentException("只有投递失败或消费失败的消息可以重试");
        jdbc.update("DELETE FROM mq_consumed_event WHERE consumer_name='PRINT_TASK_CONSUMER' AND event_id=?",row.get("event_id"));
        jdbc.update("UPDATE mq_outbox SET status='RETRY',attempt_count=0,next_attempt_time=?,sent_time=NULL,last_error=NULL,update_time=? WHERE id=?",LocalDateTime.now(),LocalDateTime.now(),id);
        return Result.success(Map.of("id",id,"status","RETRY"));}

    private List<Map<String,Object>> findEvents(String category,String status,LocalDate from,LocalDate to){StringBuilder sql=new StringBuilder("""
      SELECT id,category,level,module,message,error_class AS errorClass,request_method AS requestMethod,
        request_path AS requestPath,operator_username AS operatorUsername,store_code AS storeCode,
        client_version AS clientVersion,occurrence_count AS occurrenceCount,first_time AS firstTime,last_time AS lastTime,
        status,resolution_note AS resolutionNote,resolver_name AS resolverName,resolved_time AS resolvedTime
      FROM system_event_log WHERE (store_code=? OR store_code IS NULL)
      """);List<Object> p=new ArrayList<>();p.add(user.getStoreCode());if(category!=null&&!category.isBlank()){sql.append(" AND category=?");p.add(category.trim().toUpperCase());}if(status!=null&&!status.isBlank()){sql.append(" AND status=?");p.add(status.trim().toUpperCase());}if(from!=null){sql.append(" AND last_time>=?");p.add(from.atStartOfDay());}if(to!=null){sql.append(" AND last_time<?");p.add(to.plusDays(1).atStartOfDay());}sql.append(" ORDER BY last_time DESC LIMIT 500");return jdbc.queryForList(sql.toString(),p.toArray());}
    private Map<String,Object> check(String name,boolean online,String detail){return Map.of("name",name,"state",online?"OK":"ERROR","detail",detail);}
    private Map<String,Object> checkOptional(String name,boolean online,String detail){return Map.of("name",name,"state",online?"OK":"UNCONFIGURED","detail",online?detail:"未连接或未配置");}
    private boolean tcp(String host,int port){try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(host,port),350);return true;}catch(Exception e){return false;}}
    private String cell(Object value){String s=value==null?"":String.valueOf(value);if(s.matches("^[=+@-].*"))s="'"+s;return '"'+s.replace("\"","\"\"")+'"';}
    static void validateDates(LocalDate from,LocalDate to){if(from!=null&&to!=null&&(to.isBefore(from)||to.isAfter(from.plusDays(366))))throw new IllegalArgumentException("查询时间范围不能超过366天");}
    private void admin(){if(!"ADMIN".equals(user.getUser().getRole()))throw new AccessDeniedException("仅管理员可使用远程维护");}
}
