package com.lzj.railway.order.mq;

import java.time.LocalDateTime;

/** 与支付域事件一致的消费消息模型，订单服务不依赖支付服务的 Java 模块。 */
public record PaySuccessMessage(String orderSn, String paySn, String channel, LocalDateTime paymentTime) {
}
