package com.lzj.railway.order.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzj.railway.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** 消费支付成功消息并推进订单状态，重复消息由订单状态条件更新自然幂等。 */
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "railway-pay-result", consumerGroup = "order-service-payment-consumer")
public class PaySuccessOrderConsumer implements RocketMQListener<String> {
    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    /** 反序列化消息后执行订单状态流转；抛出异常会交给 RocketMQ 重试。 */
    @Override
    public void onMessage(String message) {
        try {
            PaySuccessMessage event = objectMapper.readValue(message, PaySuccessMessage.class);
            orderService.confirmTicketOrderPayment(event.orderSn(), event.paymentTime());
        } catch (Exception exception) {
            throw new IllegalStateException("处理支付成功消息失败", exception);
        }
    }
}
