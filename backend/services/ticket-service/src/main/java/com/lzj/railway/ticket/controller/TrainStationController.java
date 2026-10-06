package com.lzj.railway.ticket.controller;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.web.result.Results;
import com.lzj.railway.ticket.dto.response.TrainStationResponse;
import com.lzj.railway.ticket.service.TrainStationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 对外展示列车经停站与时刻表。 */
@RestController
@RequestMapping("/api/ticket/trains")
@RequiredArgsConstructor
@Tag(name = "列车行程", description = "公开查询列车经停站与到发时刻")
public class TrainStationController {
    private final TrainStationService trainStationService;

    /** 查询一列车的经停区间，返回列表顺序即列车运行顺序。 */
    @GetMapping("/{trainId}/stations")
    @Operation(summary = "查询列车经停站")
    public Result<List<TrainStationResponse>> list(@PathVariable Long trainId) {
        return Results.success(trainStationService.listTrainStations(trainId));
    }
}
