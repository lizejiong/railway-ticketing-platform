package com.lzj.railway.ticket.remote.dto;

/** 发送至订单域的单个乘车人订单明细。 */
public record TicketOrderItemCreateRemoteRequest(String carriageNumber, Integer seatType, String seatNumber,
                                                  Long passengerId, String realName, Integer idType, String idCard,
                                                  String phone, Integer amount, Integer ticketType) {
}
