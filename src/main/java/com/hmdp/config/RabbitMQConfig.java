package com.hmdp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EXCHANGE = "hmdp.order.exchange";
    public static final String ORDER_QUEUE = "hmdp.order.queue";
    public static final String ORDER_ROUTING_KEY = "order.create";

    // 死信
    public static final String ORDER_DLX_EXCHANGE = "hmdp.order.dlx.exchange";
    public static final String ORDER_DLQ = "hmdp.order.dlq";
    public static final String ORDER_DEAD_ROUTING_KEY = "order.dead";

    @Bean
    public DirectExchange orderExchange() {
        return ExchangeBuilder.directExchange(ORDER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE)
                // 消费失败 nack 后转到这里
                .withArgument("x-dead-letter-exchange", ORDER_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderQueue()).to(orderExchange()).with(ORDER_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderDlxExchange() {
        return ExchangeBuilder.directExchange(ORDER_DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue orderDlq() {
        return QueueBuilder.durable(ORDER_DLQ).build();
    }

    @Bean
    public Binding orderDlqBinding() {
        return BindingBuilder.bind(orderDlq()).to(orderDlxExchange()).with(ORDER_DEAD_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}