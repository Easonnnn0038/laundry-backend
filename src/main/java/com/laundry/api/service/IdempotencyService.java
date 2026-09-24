package com.laundry.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> begin(String scope, String requestId, Class<T> responseType) {
        int inserted = jdbc.update("""
                INSERT IGNORE INTO idempotency_request(request_scope,request_id,status)
                VALUES (?,?,'PROCESSING')
                """, scope, requestId);
        if (inserted == 1) return Optional.empty();
        return jdbc.query("""
                SELECT response_json FROM idempotency_request
                WHERE request_scope=? AND request_id=? AND status='SUCCESS'
                """, rs -> {
            if (!rs.next()) throw new IllegalStateException("相同请求正在处理中，请稍后重试");
            try {
                return Optional.of(objectMapper.readValue(rs.getString(1), responseType));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("无法读取幂等请求结果", e);
            }
        }, scope, requestId);
    }

    public void complete(String scope, String requestId, Object response) {
        try {
            int changed = jdbc.update("""
                    UPDATE idempotency_request SET status='SUCCESS',response_json=?
                    WHERE request_scope=? AND request_id=? AND status='PROCESSING'
                    """, objectMapper.writeValueAsString(response), scope, requestId);
            if (changed != 1) throw new IllegalStateException("幂等请求状态异常");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法保存幂等请求结果", e);
        }
    }
}
