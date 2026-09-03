package com.laundry.api.mq;

import com.laundry.api.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PrintTaskProducer {

    private static final Logger log = LoggerFactory.getLogger(PrintTaskProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendPrintTask(PrintTaskMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_PRINT,
                message
        );
        log.info("标签打印任务已发送 orderNo={}", message.getOrderNo());
    }
}
