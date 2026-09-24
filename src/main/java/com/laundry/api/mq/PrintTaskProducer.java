package com.laundry.api.mq;

import com.laundry.api.config.RabbitMQConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class PrintTaskProducer {

    private static final Logger log = LoggerFactory.getLogger(PrintTaskProducer.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PrintTaskProducer(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void enqueuePrintTask(PrintTaskMessage message) {
        message.setEventId(UUID.randomUUID().toString());
        try {
            jdbc.update("""
                    INSERT INTO mq_outbox(event_id,event_type,aggregate_type,aggregate_id,store_code,
                        exchange_name,routing_key,payload,status,next_attempt_time,create_time,update_time)
                    VALUES (?,'PRINT_TASK','ORDER',?,?,?,?,?,'PENDING',?,?,?)
                    """, message.getEventId(), message.getOrderNo(), message.getStoreCode(),
                    RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_PRINT,
                    objectMapper.writeValueAsString(message), LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
            log.info("标签打印任务已写入Outbox orderNo={}, eventId={}", message.getOrderNo(), message.getEventId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("打印任务序列化失败", e);
        }
    }
}
