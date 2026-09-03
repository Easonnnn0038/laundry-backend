package com.laundry.api.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "laundry.exchange";
    public static final String QUEUE_PRINT_TASK = "laundry.print.task.queue";
    public static final String ROUTING_KEY_PRINT = "laundry.print.task";
    public static final String QUEUE_DLX = "laundry.print.dlx.queue";
    public static final String ROUTING_KEY_DLX = "laundry.print.dlx";

    @Bean
    public DirectExchange exchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue printTaskQueue() {
        return QueueBuilder.durable(QUEUE_PRINT_TASK)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DLX)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(QUEUE_DLX).build();
    }

    @Bean
    public Binding printTaskBinding() {
        return BindingBuilder.bind(printTaskQueue()).to(exchange()).with(ROUTING_KEY_PRINT);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(exchange()).with(ROUTING_KEY_DLX);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
