package com.lzj.railway.order.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 将已提交订单转化为 RocketMQ 延迟消息。
 *
 * <p>RocketMQ 默认延迟级别 16 为 30 分钟；支付与关单通过订单状态条件更新竞争，最终只有一个状态流转成功。</p>
 */
@Component
@RequiredArgsConstructor
public class DelayedOrderCloseMessagePublisher {
    private static final String TOPIC = "railway-order-delay-close";
    private static final int DELAY_LEVEL = 16;
    private static final long SEND_TIMEOUT_MILLIS = 3_000L;

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /** 在订单数据库事务提交后发送延迟关闭消息。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(DelayedOrderCloseEvent event) {
        try {
            rocketMQTemplate.syncSend(TOPIC, MessageBuilder.withPayload(objectMapper.writeValueAsString(event)).build(),
                    SEND_TIMEOUT_MILLIS, DELAY_LEVEL);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化延迟关单消息失败", exception);
        }
    }
}
