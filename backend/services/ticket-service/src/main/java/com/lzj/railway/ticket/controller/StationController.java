package com.lzj.railway.ticket.controller;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.web.result.Results;
import com.lzj.railway.ticket.service.query.StationRegionCache;
import com.lzj.railway.ticket.service.query.TicketStationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 向未登录用户公开可查询车站，返回值可直接作为余票查询入参。 */
@RestController
@RequestMapping("/api/ticket/stations")
@RequiredArgsConstructor
@Tag(name = "车站查询", description = "公开的车站编码、名称和区域查询")
public class StationController {
    private final StationRegionCache stationRegionCache;

    /** 返回全部可售车站；使用 {@code code} 作为余票查询的 fromStation、toStation 入参。 */
    @GetMapping
    @Operation(summary = "查询全部车站")
    public Result<List<TicketStationResponse>> listAll() {
        return Results.success(stationRegionCache.listAllStations());
    }
}
