package com.laundry.api.mq;

import com.laundry.api.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PrintTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(PrintTaskConsumer.class);

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PRINT_TASK)
    public void handlePrintTask(PrintTaskMessage message) {
        try {
            log.info("开始处理标签打印任务 orderNo={}，衣物{}件",
                    message.getOrderNo(),
                    message.getItems() != null ? message.getItems().size() : 0);

            if (message.getItems() != null) {
                for (PrintTaskMessage.ItemPrintInfo item : message.getItems()) {
                    log.info("打印标签 条码={} 类别={} 数量={} 颜色={}",
                            item.getBarcode(), item.getCategoryName(),
                            item.getQuantity(), item.getColor());
                }
            }

            log.info("标签打印任务完成 orderNo={}", message.getOrderNo());

        } catch (Exception e) {
            log.error("标签打印任务失败 orderNo={}", message.getOrderNo(), e);
            throw e;
        }
    }
}
