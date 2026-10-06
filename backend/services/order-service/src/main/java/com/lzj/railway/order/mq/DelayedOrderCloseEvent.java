package com.lzj.railway.order.mq;

/**
 * 订单本地事务提交后发布的领域事件。
 *
 * @param orderSn 需要在支付超时后关闭的订单号
 */
public record DelayedOrderCloseEvent(String orderSn) {
}
