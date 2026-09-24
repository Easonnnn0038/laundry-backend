package com.laundry.api.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.max-publish-attempts:8}")
    private int maxAttempts;

    public OutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbitTemplate) {
        this.jdbc = jdbc;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${app.rabbitmq.outbox-poll-ms:2000}")
    @Transactional
    public void publishPending() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,event_id,event_type,aggregate_id,exchange_name,routing_key,payload,attempt_count
                FROM mq_outbox
                WHERE status IN ('PENDING','RETRY') AND next_attempt_time<=? AND attempt_count<?
                ORDER BY id LIMIT 20 FOR UPDATE SKIP LOCKED
                """, LocalDateTime.now(), maxAttempts);
        for (Map<String, Object> row : rows) publish(row);
    }

    private void publish(Map<String, Object> row) {
        long id = ((Number) row.get("id")).longValue();
        String eventId = String.valueOf(row.get("event_id"));
        int attempt = ((Number) row.get("attempt_count")).intValue() + 1;
        jdbc.update("UPDATE mq_outbox SET status='PUBLISHING',attempt_count=?,update_time=? WHERE id=?",
                attempt, LocalDateTime.now(), id);
        try {
            Message message = MessageBuilder
                    .withBody(String.valueOf(row.get("payload")).getBytes(StandardCharsets.UTF_8))
                    .setContentType("application/json")
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(eventId)
                    .setHeader("eventId", eventId)
                    .setHeader("eventType", row.get("event_type"))
                    .setHeader("aggregateId", row.get("aggregate_id"))
                    .build();
            CorrelationData correlation = new CorrelationData(eventId);
            rabbitTemplate.send(String.valueOf(row.get("exchange_name")),
                    String.valueOf(row.get("routing_key")), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) throw new IllegalStateException("Broker NACK: " + confirm.getReason());
            if (correlation.getReturned() != null) throw new IllegalStateException("消息无可用路由");
            jdbc.update("UPDATE mq_outbox SET status='SENT',sent_time=?,last_error=NULL,update_time=? WHERE id=?",
                    LocalDateTime.now(), LocalDateTime.now(), id);
            log.info("Outbox消息投递成功 eventId={}, aggregateId={}", eventId, row.get("aggregate_id"));
        } catch (Exception e) {
            boolean dead = attempt >= maxAttempts;
            jdbc.update("UPDATE mq_outbox SET status=?,next_attempt_time=?,last_error=?,update_time=? WHERE id=?",
                    dead ? "DEAD" : "RETRY", LocalDateTime.now().plusSeconds(nextDelaySeconds(attempt)),
                    truncate(e.getMessage()), LocalDateTime.now(), id);
            log.warn("Outbox消息投递失败 eventId={}, attempt={}/{}, dead={}: {}",
                    eventId, attempt, maxAttempts, dead, e.getMessage());
        }
    }

    static long nextDelaySeconds(int attempt) {
        return Math.min(300, 5L << Math.min(Math.max(attempt - 1, 0), 6));
    }

    private String truncate(String value) {
        if (value == null) return "未知错误";
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
