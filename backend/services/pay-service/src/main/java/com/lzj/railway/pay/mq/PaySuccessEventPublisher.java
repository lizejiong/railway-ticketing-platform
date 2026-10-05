package com.lzj.railway.pay.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 在支付单事务提交后发送消息，避免订单先收到一笔最终会回滚的支付成功事件。 */
@Component
@RequiredArgsConstructor
public class PaySuccessEventPublisher {
    public static final String TOPIC = "railway-pay-result";
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /** 事务提交完成后同步投递，让回调失败时可由支付宝重试。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PaySuccessEvent event) {
        try {
            rocketMQTemplate.syncSend(TOPIC, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("支付成功事件序列化失败", exception);
        }
    }
}
