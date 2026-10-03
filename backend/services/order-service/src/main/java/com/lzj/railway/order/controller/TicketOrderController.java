package com.lzj.railway.order.controller;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.web.result.Results;
import com.lzj.railway.order.dto.request.CancelTicketOrderRequest;
import com.lzj.railway.order.dto.request.TicketOrderCreateRequest;
import com.lzj.railway.order.dto.response.TicketOrderResponse;
import com.lzj.railway.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 票务服务调用的订单创建、读取与取消接口。 */
@RestController
@RequestMapping("/api/order/ticket")
@RequiredArgsConstructor
public class TicketOrderController {
    private final OrderService orderService;

    /** 创建待支付订单。 */
    @PostMapping("/create")
    public Result<String> create(@RequestBody TicketOrderCreateRequest request) {
        return Results.success(orderService.createTicketOrder(request));
    }

    /** 查询归属当前用户名的订单快照。 */
    @GetMapping("/query")
    public Result<TicketOrderResponse> query(@RequestParam String orderSn, @RequestParam String username) {
        return Results.success(orderService.queryTicketOrder(orderSn, username));
    }

    /** 取消待支付订单。 */
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestBody CancelTicketOrderRequest request) {
        orderService.cancelTicketOrder(request);
        return Results.success();
    }
}
