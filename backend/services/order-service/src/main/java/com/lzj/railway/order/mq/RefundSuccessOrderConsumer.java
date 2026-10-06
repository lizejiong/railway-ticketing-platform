package com.lzj.railway.order.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzj.railway.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** 将退款成功事件同步为订单明细状态。 */
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "railway-refund-result", consumerGroup = "order-service-refund-consumer")
public class RefundSuccessOrderConsumer implements RocketMQListener<String> {
    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    @Override public void onMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            java.util.List<Long> itemIds = new java.util.ArrayList<>();
            for (JsonNode item : root.path("items")) itemIds.add(item.path("orderItemId").asLong());
            orderService.refundTicketOrder(root.path("orderSn").asText(), itemIds);
        } catch (Exception exception) { throw new IllegalStateException("处理退款订单消息失败", exception); }
    }
}
