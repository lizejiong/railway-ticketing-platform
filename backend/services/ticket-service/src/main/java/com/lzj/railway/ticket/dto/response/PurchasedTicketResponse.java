package com.lzj.railway.ticket.dto.response;

/** 已成功锁定的单张车票概要。 */
public record PurchasedTicketResponse(Long passengerId, String carriageNumber, Integer seatType,
                                      String seatNumber, Integer amount) {
}
