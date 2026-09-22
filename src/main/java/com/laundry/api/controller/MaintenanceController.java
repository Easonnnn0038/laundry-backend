package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.service.MaintenanceLogService;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

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
    private final JdbcTemplate jdbc; private final CurrentUserUtil user; private final MaintenanceLogService logs;
    public MaintenanceController(JdbcTemplate jdbc,CurrentUserUtil user,MaintenanceLogService logs){this.jdbc=jdbc;this.user=user;this.logs=logs;}
    @Value("${maintenance.factory-host:127.0.0.1}") private String factoryHost;
    @Value("${maintenance.factory-port:8081}") private int factoryPort;
    @Value("${spring.rabbitmq.host:127.0.0.1}") private String rabbitHost;
    @Value("${spring.rabbitmq.port:5672}") private int rabbitPort;
    public record ResolveRequest(@NotBlank String status,String note){}
    public record ClientErrorRequest(String module,@NotBlank String message,String path,String clientVersion){}

    @GetMapping("/status") public Result<Map<String,Object>> status(){admin();
        Map<String,Object> result=new LinkedHashMap<>();result.put("backend",check("门店后端",true,"1.0.0"));
        boolean db;try{db=jdbc.queryForObject("SELECT 1",Integer.class)==1;}catch(Exception e){db=false;}result.put("database",check("MySQL 数据库",db,db?"连接正常":"连接失败"));
        result.put("factory",check("工厂后端",tcp(factoryHost,factoryPort),factoryHost+":"+factoryPort));
        result.put("rabbitmq",checkOptional("RabbitMQ",tcp(rabbitHost,rabbitPort),rabbitHost+":"+rabbitPort));
        result.put("startedAt",LocalDateTime.now().minusSeconds(ManagementFactory.getRuntimeMXBean().getUptime()/1000));
        result.put("uptimeSeconds",ManagementFactory.getRuntimeMXBean().getUptime()/1000);result.put("checkedAt",LocalDateTime.now());return Result.success(result);
    }

    @GetMapping("/events") public Result<List<Map<String,Object>>> events(@RequestParam(required=false) String category,@RequestParam(required=false) String status,
        @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){admin();return Result.success(findEvents(category,status,from,to));}

    @PostMapping("/events/{id}/status") public Result<Map<String,Object>> resolve(@PathVariable long id,@Valid @RequestBody ResolveRequest req){admin();
        String status=req.status().trim().toUpperCase();if(!List.of("OPEN","PROCESSING","RESOLVED","IGNORED").contains(status))throw new IllegalArgumentException("无效处理状态");
        String note=req.note()==null?null:req.note().trim();if(("RESOLVED".equals(status)||"IGNORED".equals(status))&&(note==null||note.isEmpty()))throw new IllegalArgumentException("解决或忽略时必须填写处理说明");
        int changed=jdbc.update("UPDATE system_event_log SET status=?,resolution_note=?,resolver_name=?,resolved_time=? WHERE id=?",status,note,user.getOperatorName(),List.of("RESOLVED","IGNORED").contains(status)?LocalDateTime.now():null,id);
        if(changed!=1)throw new IllegalArgumentException("日志不存在");return Result.success(Map.of("id",id,"status",status));}

    @GetMapping("/audit") public Result<List<Map<String,Object>>> audit(@RequestParam(required=false) String keyword,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){admin();
        String term=keyword==null?"":keyword.trim();LocalDate start=from==null?LocalDate.now().minusDays(30):from;LocalDate end=to==null?LocalDate.now():to;
        return Result.success(jdbc.queryForList("""
          SELECT l.id,l.order_no AS orderNo,l.operate_type AS operateType,l.operate_desc AS description,
            l.before_status AS beforeStatus,l.after_status AS afterStatus,l.operator_name AS operatorName,
            l.operate_time AS operateTime,l.remark FROM order_operate_log l JOIN laundry_order o ON o.id=l.order_id
          WHERE o.store_code=? AND l.operate_time>=? AND l.operate_time<? AND (?='' OR l.order_no=? OR l.operator_name LIKE CONCAT('%',?,'%'))
          ORDER BY l.operate_time DESC LIMIT 500
          """,user.getStoreCode(),start.atStartOfDay(),end.plusDays(1).atStartOfDay(),term,term,term));}

    @GetMapping("/events/export") public ResponseEntity<byte[]> export(@RequestParam(required=false) String category,@RequestParam(required=false) String status,
        @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){admin();List<Map<String,Object>> rows=findEvents(category,status,from,to);
        StringBuilder csv=new StringBuilder("\uFEFF编号,分类,级别,模块,信息,路径,操作员,门店,次数,首次时间,最近时间,状态,处理说明\r\n");
        for(Map<String,Object> r:rows)csv.append(cell(r.get("id"))).append(',').append(cell(r.get("category"))).append(',').append(cell(r.get("level"))).append(',').append(cell(r.get("module"))).append(',').append(cell(r.get("message"))).append(',').append(cell(r.get("requestPath"))).append(',').append(cell(r.get("operatorUsername"))).append(',').append(cell(r.get("storeCode"))).append(',').append(cell(r.get("occurrenceCount"))).append(',').append(cell(r.get("firstTime"))).append(',').append(cell(r.get("lastTime"))).append(',').append(cell(r.get("status"))).append(',').append(cell(r.get("resolutionNote"))).append("\r\n");
        HttpHeaders h=new HttpHeaders();h.setContentType(new MediaType("text","csv",StandardCharsets.UTF_8));h.setContentDisposition(ContentDisposition.attachment().filename("maintenance-events-"+LocalDate.now()+".csv").build());return ResponseEntity.ok().headers(h).body(csv.toString().getBytes(StandardCharsets.UTF_8));}

    @PostMapping("/client-error") public Result<Void> clientError(@Valid @RequestBody ClientErrorRequest req){logs.recordClient(req.module()==null?"门店前端":req.module(),req.message(),req.path(),req.clientVersion());return Result.success();}

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
    private void admin(){if(!"ADMIN".equals(user.getUser().getRole()))throw new AccessDeniedException("仅管理员可使用远程维护");}
}
