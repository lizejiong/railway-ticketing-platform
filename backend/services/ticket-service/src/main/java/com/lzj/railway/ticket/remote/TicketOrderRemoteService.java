package com.lzj.railway.ticket.remote;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.ticket.remote.dto.CancelTicketOrderRemoteRequest;
import com.lzj.railway.ticket.remote.dto.TicketOrderCreateRemoteRequest;
import com.lzj.railway.ticket.remote.dto.TicketOrderRemoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** 票务域调用订单域的内部契约。 */
@FeignClient(name = "order-service")
public interface TicketOrderRemoteService {
    /** 创建待支付订单。 */
    @PostMapping("/api/order/ticket/create")
    Result<String> create(@RequestBody TicketOrderCreateRemoteRequest request);

    /** 查询指定用户名拥有的订单及明细。 */
    @GetMapping("/api/order/ticket/query")
    Result<TicketOrderRemoteResponse> query(@RequestParam("orderSn") String orderSn,
                                            @RequestParam("username") String username);

    /** 原子关闭待支付订单。 */
    @PostMapping("/api/order/ticket/cancel")
    Result<Void> cancel(@RequestBody CancelTicketOrderRemoteRequest request);
}
