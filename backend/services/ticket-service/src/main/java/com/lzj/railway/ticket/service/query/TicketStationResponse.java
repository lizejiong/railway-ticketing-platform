package com.lzj.railway.ticket.service.query;

/** 供前端选择出发、到达站使用的公开站点信息。 */
public record TicketStationResponse(String code, String name, String regionName) {
}
