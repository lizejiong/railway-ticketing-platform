package com.lzj.railway.pay.controller;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.web.result.Results;
import com.lzj.railway.pay.dto.request.CreatePaymentRequest;
import com.lzj.railway.pay.dto.request.RefundPaymentRequest;
import com.lzj.railway.pay.dto.response.PaymentInfoResponse;
import com.lzj.railway.pay.dto.response.PaymentResponse;
import com.lzj.railway.pay.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 面向登录用户的支付单创建与查询入口。 */
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
@Tag(name = "订单支付", description = "支付单创建与支付状态查询")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {
    private final PaymentService paymentService;

    /** 创建或复用当前订单的支付宝付款页。 */
    @PostMapping("/create")
    @Operation(summary = "创建支付单")
    public Result<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        return Results.success(paymentService.create(request));
    }

    /** 查询当前登录用户订单的支付状态。 */
    @GetMapping("/orders/{orderSn}")
    @Operation(summary = "查询支付状态")
    public Result<PaymentInfoResponse> query(@PathVariable String orderSn) {
        return Results.success(paymentService.queryByOrderSn(orderSn));
    }

    /** 仅供票务服务调用；该路径不应配置网关路由。 */
    @PostMapping("/internal/refund")
    public Result<Void> refund(@RequestBody RefundPaymentRequest request) {
        paymentService.refund(request);
        return Results.success();
    }
}
