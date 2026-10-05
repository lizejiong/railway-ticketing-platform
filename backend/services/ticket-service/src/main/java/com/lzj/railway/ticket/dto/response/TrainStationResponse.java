package com.lzj.railway.ticket.dto.response;

import java.time.LocalDateTime;

/** 列车行程中一个经停区间的到发信息。 */
public record TrainStationResponse(
        Integer sequence,
        String departure,
        String arrival,
        LocalDateTime arrivalTime,
        LocalDateTime departureTime,
        Integer stopoverTime) {
}
