package com.lzj.railway.pay.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 退款记录提交后再广播事件，避免下游观察到已回滚的退款。 */
@Component
@RequiredArgsConstructor
public class RefundSuccessEventPublisher {
    public static final String TOPIC = "railway-refund-result";
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(RefundSuccessEvent event) {
        try {
            rocketMQTemplate.syncSend(TOPIC, objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            throw new IllegalStateException("退款成功事件发送失败", exception);
        }
    }
}
