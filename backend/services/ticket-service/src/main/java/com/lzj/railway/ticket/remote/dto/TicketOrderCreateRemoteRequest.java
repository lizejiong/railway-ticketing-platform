package com.lzj.railway.ticket.remote.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 发送至订单域的待支付订单创建请求。 */
public record TicketOrderCreateRemoteRequest(Long userId, String username, Long trainId, String departure,
                                             String arrival, Integer source, LocalDateTime orderTime,
                                             LocalDateTime ridingDate, String trainNumber,
                                             LocalDateTime departureTime, LocalDateTime arrivalTime,
                                             List<TicketOrderItemCreateRemoteRequest> ticketOrderItems) {
}
