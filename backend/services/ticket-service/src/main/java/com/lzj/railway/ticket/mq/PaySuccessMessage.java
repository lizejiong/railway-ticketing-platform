package com.lzj.railway.ticket.mq;

import java.time.LocalDateTime;

/** 与支付域消息一致的本地消费模型，避免票务模块依赖支付模块。 */
public record PaySuccessMessage(String orderSn, String paySn, String channel, LocalDateTime paymentTime) {
}
