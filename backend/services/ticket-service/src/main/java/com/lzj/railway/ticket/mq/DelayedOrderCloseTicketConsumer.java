package com.lzj.railway.ticket.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzj.railway.ticket.service.purchase.TicketPurchaseService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 消费订单超时关闭消息，回收未支付订单锁定的座位及余票令牌。
 */
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "railway-order-delay-close", consumerGroup = "ticket-service-delay-close-consumer")
public class DelayedOrderCloseTicketConsumer implements RocketMQListener<String> {
    private final ObjectMapper objectMapper;
    private final TicketPurchaseService ticketPurchaseService;

    /** 消息处理失败时抛出异常，使 RocketMQ 按消费者重试策略重新投递。 */
    @Override
    public void onMessage(String message) {
        try {
            DelayedOrderCloseMessage event = objectMapper.readValue(message, DelayedOrderCloseMessage.class);
            ticketPurchaseService.closeExpired(event.orderSn());
        } catch (Exception exception) {
            throw new IllegalStateException("处理延迟关单消息失败", exception);
        }
    }
}
