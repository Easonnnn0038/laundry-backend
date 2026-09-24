package com.laundry.api.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PrintTaskHandler {
    public static final String CONSUMER = "PRINT_TASK_CONSUMER";
    private static final Logger log = LoggerFactory.getLogger(PrintTaskHandler.class);
    private final JdbcTemplate jdbc;

    public PrintTaskHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean handle(PrintTaskMessage message) {
        if (message.getEventId() == null || message.getEventId().isBlank()) {
            throw new IllegalArgumentException("打印消息缺少eventId");
        }
        int inserted = jdbc.update("""
                INSERT IGNORE INTO mq_consumed_event(consumer_name,event_id,event_type,aggregate_id,status,create_time,update_time)
                VALUES (?,?,'PRINT_TASK',?,'PROCESSING',?,?)
                """, CONSUMER, message.getEventId(), message.getOrderNo(), LocalDateTime.now(), LocalDateTime.now());
        if (inserted == 0) {
            String status = jdbc.queryForObject("""
                    SELECT status FROM mq_consumed_event WHERE consumer_name=? AND event_id=?
                    """, String.class, CONSUMER, message.getEventId());
            if ("SUCCESS".equals(status)) return false;
            throw new IllegalStateException("消息已有未完成处理记录: " + status);
        }

        log.info("开始处理标签打印任务 orderNo={}，衣物{}件", message.getOrderNo(),
                message.getItems() == null ? 0 : message.getItems().size());
        if (message.getItems() != null) {
            for (PrintTaskMessage.ItemPrintInfo item : message.getItems()) {
                log.info("打印标签 条码={} 类别={} 数量={} 颜色={}", item.getBarcode(),
                        item.getCategoryName(), item.getQuantity(), item.getColor());
            }
        }
        jdbc.update("""
                UPDATE mq_consumed_event SET status='SUCCESS',processed_time=?,last_error=NULL,update_time=?
                WHERE consumer_name=? AND event_id=? AND status='PROCESSING'
                """, LocalDateTime.now(), LocalDateTime.now(), CONSUMER, message.getEventId());
        return true;
    }
}
