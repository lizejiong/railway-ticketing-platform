package com.lzj.railway.ticket.dto.response;

import java.util.List;

/** 提交购票成功后的订单号和已分配座位。 */
public record PurchaseTicketResponse(String orderSn, List<PurchasedTicketResponse> tickets) {
}
