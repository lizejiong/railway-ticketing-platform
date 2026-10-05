package com.lzj.railway.pay.remote;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.pay.remote.dto.OrderPaymentRemoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 支付域读取订单快照的内部契约。 */
@FeignClient(name = "order-service")
public interface OrderPaymentRemoteService {
    /** 获取指定用户拥有的订单与票价快照。 */
    @GetMapping("/api/order/ticket/query")
    Result<OrderPaymentRemoteResponse> query(@RequestParam("orderSn") String orderSn,
                                             @RequestParam("username") String username);
}
