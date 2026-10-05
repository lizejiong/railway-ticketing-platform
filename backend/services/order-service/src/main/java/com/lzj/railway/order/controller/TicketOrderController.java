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

    /**
     * 仅供订阅支付事件的内部服务读取订单座位快照。
     *
     * <p>该接口不配置网关路由，运行环境需通过服务网络隔离保证仅内部调用。</p>
     */
    @GetMapping("/internal/query")
    public Result<TicketOrderResponse> queryInternal(@RequestParam String orderSn) {
        return Results.success(orderService.queryTicketOrderInternal(orderSn));
    }

    /**
     * 仅供票务域消费超时关闭消息时调用。
     *
     * <p>接口不经过网关暴露；条件更新保证支付成功和延迟关闭并发到达时，只有一个状态流转可以成功。</p>
     */
    @PostMapping("/internal/close")
    public Result<Boolean> closeExpired(@RequestParam String orderSn) {
        return Results.success(orderService.closeExpiredTicketOrder(orderSn));
    }

    /** 仅供支付退款成功事件消费者调用。 */
    @PostMapping("/internal/refund")
    public Result<Void> refund(@RequestParam String orderSn, @RequestBody java.util.List<Long> orderItemIds) {
        orderService.refundTicketOrder(orderSn, orderItemIds);
        return Results.success();
    }

    /** 取消待支付订单。 */
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestBody CancelTicketOrderRequest request) {
        orderService.cancelTicketOrder(request);
        return Results.success();
    }
}
