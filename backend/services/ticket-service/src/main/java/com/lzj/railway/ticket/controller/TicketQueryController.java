package com.lzj.railway.ticket.controller;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.web.result.Results;
import com.lzj.railway.ticket.dto.request.TicketQueryRequest;
import com.lzj.railway.ticket.dto.response.TicketQueryResponse;
import com.lzj.railway.ticket.service.TicketQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对外提供公开的车次与余票查询接口。
 */
@RestController
@RequestMapping("/api/ticket")
@RequiredArgsConstructor
@Tag(name = "票务查询", description = "公开的车次与余票查询接口")
public class TicketQueryController {

    private final TicketQueryService ticketQueryService;

    /**
     * 按出发站编码、到达站编码和乘车日期查询可售车次。
     *
     * @param request 查询条件，fromStation、toStation 对应 t_station.code
     * @return 可售车次及席别余票
     */
    @GetMapping("/query")
    @Operation(summary = "查询车次和余票", description = "fromStation、toStation 使用 t_station.code，例如 VNP、NKH")
    public Result<List<TicketQueryResponse>> query(@Valid @ModelAttribute TicketQueryRequest request) {
        return Results.success(ticketQueryService.query(request));
    }
}
