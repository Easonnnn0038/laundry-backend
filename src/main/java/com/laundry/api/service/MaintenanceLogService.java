package com.laundry.api.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class MaintenanceLogService {
    private final JdbcTemplate jdbc;
    public MaintenanceLogService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public void record(String category,String level,String module,String message,Throwable error,HttpServletRequest request){
        try{
            String safe=sanitize(message); String method=request==null?null:request.getMethod();
            String path=request==null?null:sanitize(request.getRequestURI()); String username=currentUsername();
            String store=storeCode(username); String errorClass=error==null?null:error.getClass().getName();
            String fingerprint=sha(category+"|"+module+"|"+safe+"|"+method+"|"+path);
            LocalDateTime now=LocalDateTime.now();
            List<Long> existing=jdbc.query("""
                SELECT id FROM system_event_log WHERE fingerprint=? AND status='OPEN'
                  AND last_time>=? ORDER BY id DESC LIMIT 1
                """,(rs,n)->rs.getLong(1),fingerprint,now.minusHours(24));
            if(existing.isEmpty()) jdbc.update("""
                INSERT INTO system_event_log(fingerprint,category,level,module,message,error_class,request_method,
                  request_path,operator_username,store_code,first_time,last_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,fingerprint,category,level,module,safe,errorClass,method,path,username,store,now,now);
            else jdbc.update("UPDATE system_event_log SET occurrence_count=occurrence_count+1,last_time=?,operator_username=?,store_code=? WHERE id=?",
                    now,username,store,existing.get(0));
        }catch(Exception ignored){ /* 日志失败不能覆盖原业务异常 */ }
    }

    public void recordClient(String module,String message,String path,String version){
        try{
            String safe=sanitize(message);String safePath=sanitize(path);String username=currentUsername();String store=storeCode(username);
            String fingerprint=sha("CLIENT_ERROR|"+module+"|"+safe+"|"+safePath);LocalDateTime now=LocalDateTime.now();
            jdbc.update("""
              INSERT INTO system_event_log(fingerprint,category,level,module,message,request_path,operator_username,
                store_code,client_version,first_time,last_time) VALUES (?,'CLIENT_ERROR','WARN',?,?,?,?,?,?,?,?)
              """,fingerprint,module,safe,safePath,username,store,sanitize(version),now,now);
        }catch(Exception ignored){}
    }
    private String currentUsername(){Authentication a=SecurityContextHolder.getContext().getAuthentication();return a==null||!a.isAuthenticated()||"anonymousUser".equals(a.getName())?null:a.getName();}
    private String storeCode(String username){if(username==null)return null;try{return jdbc.queryForObject("SELECT store_id FROM users WHERE username=?",String.class,username);}catch(DataAccessException e){return null;}}
    private String sanitize(String value){if(value==null)return null;String s=value.replaceAll("(?i)(password|token|secret|authorization)(\\s*[:=]\\s*)[^,;\\s]+","$1$2[已过滤]");return s.length()>1000?s.substring(0,1000):s;}
    private String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){return Integer.toHexString(value.hashCode());}}
}
