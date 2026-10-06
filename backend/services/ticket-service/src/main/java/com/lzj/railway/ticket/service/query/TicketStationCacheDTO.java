package com.lzj.railway.ticket.service.query;

/**
 * Redis 中的车站映射信息。
 *
 * @param code 车站编码
 * @param name 车站展示名称
 * @param regionName 所属区域展示名称
 */
public record TicketStationCacheDTO(String code, String name, String regionName) {
}
