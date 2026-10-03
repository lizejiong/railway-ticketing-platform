package com.lzj.railway.order.dto.request;

/** 取消待支付订单的请求。 */
public record CancelTicketOrderRequest(String orderSn, String username) {
}
