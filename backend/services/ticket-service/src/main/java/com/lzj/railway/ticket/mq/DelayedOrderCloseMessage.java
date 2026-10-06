package com.lzj.railway.ticket.mq;

/** 与订单域延迟关闭消息一致的跨服务传输模型。 */
public record DelayedOrderCloseMessage(String orderSn) {
}
