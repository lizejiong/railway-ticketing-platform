package com.lzj.railway.ticket.service.purchase;

/**
 * 列车行程中不可再拆分的相邻站点区间。
 *
 * @param departure 区间出发站名称
 * @param arrival 区间到达站名称
 */
public record TrainRouteSegment(String departure, String arrival) {
}
