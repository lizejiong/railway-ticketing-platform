package com.lzj.railway.pay.remote.dto;

import java.util.List;

/** 支付所需的订单最小快照，字段与订单服务查询响应保持一致。 */
public record OrderPaymentRemoteResponse(String orderSn, String username, String trainNumber, Integer status,
                                         List<OrderPaymentItemRemoteResponse> passengerDetails) {
}
