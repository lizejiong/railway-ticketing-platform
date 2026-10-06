package com.lzj.railway.ticket.remote.dto;

/** 单个乘车人对应的退款明细快照。 */
public record RefundPaymentItemRemoteRequest(Long orderItemId, Integer amount, Integer seatType,
                                             String carriageNumber, String seatNumber, Integer idType,
                                             String idCard, String realName) {
}
