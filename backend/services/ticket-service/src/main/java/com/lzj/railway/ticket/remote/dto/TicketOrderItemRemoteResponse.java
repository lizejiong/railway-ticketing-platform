package com.lzj.railway.ticket.remote.dto;

/** 订单域返回的乘车人订单明细。 */
public record TicketOrderItemRemoteResponse(Long id, String carriageNumber, Integer seatType, String seatNumber,
                                             Long passengerId, String realName, Integer idType, String idCard,
                                             String phone, Integer amount, Integer ticketType, Integer status) {
}
