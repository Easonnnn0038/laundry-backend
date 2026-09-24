package com.laundry.api.mq;

import com.laundry.api.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class PrintTaskDeadLetterConsumer {
    private final JdbcTemplate jdbc;

    public PrintTaskDeadLetterConsumer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DLX)
    public void record(PrintTaskMessage task, Message raw, Channel channel) throws IOException {
        long tag = raw.getMessageProperties().getDeliveryTag();
        try {
            jdbc.update("""
                    INSERT INTO mq_consumed_event(consumer_name,event_id,event_type,aggregate_id,status,last_error,create_time,update_time)
                    VALUES (?,?,'PRINT_TASK',?,'DEAD','消费重试耗尽',?,?)
                    ON DUPLICATE KEY UPDATE status=IF(status='SUCCESS','SUCCESS','DEAD'),
                        last_error=IF(status='SUCCESS',last_error,'消费重试耗尽'),update_time=VALUES(update_time)
                    """, PrintTaskHandler.CONSUMER, task.getEventId(), task.getOrderNo(),
                    LocalDateTime.now(), LocalDateTime.now());
            channel.basicAck(tag, false);
        } catch (Exception e) {
            channel.basicNack(tag, false, true);
        }
    }
}
