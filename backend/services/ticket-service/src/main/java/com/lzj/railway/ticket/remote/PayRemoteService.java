package com.lzj.railway.ticket.remote;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.ticket.remote.dto.RefundPaymentRemoteRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 票务域向支付域发起退款的内部契约。 */
@FeignClient(name = "pay-service")
public interface PayRemoteService {
    /** 根据已校验的订单快照执行退款。 */
    @PostMapping("/api/pay/internal/refund")
    Result<Void> refund(@RequestBody RefundPaymentRemoteRequest request);
}
