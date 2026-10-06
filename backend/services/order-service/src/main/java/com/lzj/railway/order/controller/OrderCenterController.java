package com.lzj.railway.order.controller;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.convention.page.PageResponse;
import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.user.core.UserContext;
import com.lzj.railway.framework.starter.web.result.Results;
import com.lzj.railway.order.common.OrderErrorCode;
import com.lzj.railway.order.dto.request.TicketOrderPageRequest;
import com.lzj.railway.order.dto.response.TicketOrderPageResponse;
import com.lzj.railway.order.dto.response.TicketOrderResponse;
import com.lzj.railway.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 面向登录用户的订单中心查询入口。 */
@RestController
@RequestMapping("/api/order/tickets")
@RequiredArgsConstructor
public class OrderCenterController {
    private final OrderService orderService;

    /** 分页读取当前登录用户的订单摘要。 */
    @GetMapping
    public Result<PageResponse<TicketOrderPageResponse>> page(@Valid TicketOrderPageRequest request) {
        return Results.success(orderService.pageTicketOrders(currentUserId(), request));
    }

    /** 查询当前登录用户拥有的一笔订单详情。 */
    @GetMapping("/{orderSn}")
    public Result<TicketOrderResponse> detail(@PathVariable String orderSn) {
        return Results.success(orderService.queryTicketOrder(orderSn, currentUsername()));
    }

    private Long currentUserId() {
        try {
            return Long.parseLong(UserContext.getUserId());
        } catch (NumberFormatException | NullPointerException exception) {
            throw new ClientException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    private String currentUsername() {
        String username = UserContext.getUsername();
        if (!StringUtils.hasText(username)) {
            throw new ClientException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
        return username;
    }
}
