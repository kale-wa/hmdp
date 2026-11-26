package com.hmdp.mq;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {

    private final IVoucherOrderService voucherOrderService;
    private final StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    public void onMessage(VoucherOrder order, Channel channel, Message message) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String idempotentKey = "mq:order:" + order.getId();

        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", 1, TimeUnit.DAYS);
        if (Boolean.FALSE.equals(first)) {
            channel.basicAck(tag, false);   // 重复消息，直接确认
            return;
        }

        try {
            voucherOrderService.createVoucherOrder(order);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("创建订单失败, orderId={}", order.getId(), e);
            stringRedisTemplate.delete(idempotentKey);
            channel.basicNack(tag, false, false);   // 不重回队列，进死信
        }
    }
}