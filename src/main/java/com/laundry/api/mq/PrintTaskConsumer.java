package com.laundry.api.mq;

import com.laundry.api.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PrintTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(PrintTaskConsumer.class);
    private final PrintTaskHandler handler;
    @Value("${app.rabbitmq.consumer-max-attempts:3}") private int maxAttempts;
    @Value("${app.rabbitmq.consumer-retry-interval-ms:2000}") private long retryIntervalMs;

    public PrintTaskConsumer(PrintTaskHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PRINT_TASK)
    public void handlePrintTask(PrintTaskMessage task, Message raw, Channel channel) throws IOException {
        long tag = raw.getMessageProperties().getDeliveryTag();
        // ponytail: 单消费者短暂阻塞重试；打印吞吐明显上升时再换延迟重试队列。
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                boolean processed = handler.handle(task);
                channel.basicAck(tag, false);
                log.info("标签打印消息已确认 orderNo={}, eventId={}, duplicate={}",
                        task.getOrderNo(), task.getEventId(), !processed);
                return;
            } catch (Exception e) {
                log.warn("标签打印消息处理失败 orderNo={}, attempt={}/{}: {}",
                        task.getOrderNo(), attempt, maxAttempts, e.getMessage());
                if (attempt == maxAttempts) {
                    channel.basicNack(tag, false, false);
                    return;
                }
                try {
                    Thread.sleep(retryIntervalMs * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    channel.basicNack(tag, false, false);
                    return;
                }
            }
        }
    }
}
