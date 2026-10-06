package com.lzj.railway.pay.mq;

import java.time.LocalDateTime;

/** 支付单已确认成功后的领域事件，由订单服务消费。 */
public record PaySuccessEvent(String orderSn, String paySn, String channel, LocalDateTime paymentTime) {
}
