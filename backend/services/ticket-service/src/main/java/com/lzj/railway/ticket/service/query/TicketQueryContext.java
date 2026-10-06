package com.lzj.railway.ticket.service.query;

/**
 * 已完成参数和车站映射校验的票务查询上下文。
 *
 * @param fromStationCode 出发站编码
 * @param toStationCode 到达站编码
 * @param fromStationName 出发站名称
 * @param toStationName 到达站名称
 * @param fromRegion 出发地区名称
 * @param toRegion 到达地区名称
 */
public record TicketQueryContext(
        String fromStationCode,
        String toStationCode,
        String fromStationName,
        String toStationName,
        String fromRegion,
        String toRegion) {
}
