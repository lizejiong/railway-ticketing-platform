package com.lzj.railway.ticket.controller;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.web.result.Results;
import com.lzj.railway.ticket.dto.request.PurchaseTicketRequest;
import com.lzj.railway.ticket.dto.request.RefundTicketRequest;
import com.lzj.railway.ticket.dto.response.PurchaseTicketResponse;
import com.lzj.railway.ticket.service.purchase.TicketPurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 下单锁座与取消订单的票务入口。 */
@RestController
@RequestMapping("/api/ticket")
@RequiredArgsConstructor
@Tag(name = "购票下单", description = "座位锁定、订单创建与取消")
@SecurityRequirement(name = "bearerAuth")
public class TicketPurchaseController {
    private final TicketPurchaseService ticketPurchaseService;

    /** 锁定实体座位并创建待支付订单。 */
    @PostMapping("/purchase")
    @Operation(summary = "提交购票")
    public Result<PurchaseTicketResponse> purchase(@Valid @RequestBody PurchaseTicketRequest request) {
        return Results.success(ticketPurchaseService.purchase(request));
    }

    /** 取消待支付订单并同步释放座位和余票。 */
    @PostMapping("/orders/{orderSn}/cancel")
    @Operation(summary = "取消待支付订单")
    public Result<Void> cancel(@PathVariable String orderSn) {
        ticketPurchaseService.cancel(orderSn);
        return Results.success();
    }

    /** 对已支付订单发起整单或部分退票。 */
    @PostMapping("/orders/{orderSn}/refund")
    @Operation(summary = "申请退票")
    public Result<Void> refund(@PathVariable String orderSn, @RequestBody RefundTicketRequest request) {
        ticketPurchaseService.refund(orderSn, request);
        return Results.success();
    }
}
