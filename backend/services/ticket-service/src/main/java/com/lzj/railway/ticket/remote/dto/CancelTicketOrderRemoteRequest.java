package com.lzj.railway.ticket.remote.dto;

/** 发送至订单域的订单取消请求。 */
public record CancelTicketOrderRemoteRequest(String orderSn, String username) {
}
