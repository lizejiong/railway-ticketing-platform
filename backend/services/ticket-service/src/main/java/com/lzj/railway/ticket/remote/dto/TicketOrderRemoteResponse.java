package com.lzj.railway.ticket.remote.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 订单域返回的订单与明细快照。 */
public record TicketOrderRemoteResponse(String orderSn, Long userId, String username, Long trainId,
                                        String trainNumber, String departure, String arrival,
                                        LocalDateTime departureTime, LocalDateTime arrivalTime, Integer status,
                                        List<TicketOrderItemRemoteResponse> passengerDetails) {
}
