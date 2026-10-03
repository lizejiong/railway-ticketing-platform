package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.framework.designpattern.chain.ResponsibilityChain;
import com.lzj.railway.ticket.dto.request.PurchaseTicketRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 提交购票前的责任链入口。
 */
@Component
public class PurchaseTicketValidationChain {

    private final ResponsibilityChain<PurchaseTicketContext> responsibilityChain;

    /**
     * 收集并按 order 装配所有购票校验处理器。
     *
     * @param handlers 购票责任链处理器
     */
    public PurchaseTicketValidationChain(List<ChainHandler<PurchaseTicketContext>> handlers) {
        this.responsibilityChain = ResponsibilityChain.<PurchaseTicketContext>builder()
                .addAll(handlers)
                .build();
    }

    /**
     * 校验购票请求并返回包含车站名称、连续区间的上下文。
     *
     * @param request 客户端提交的购票请求
     * @return 后续扣减库存和锁座可直接使用的上下文
     */
    public PurchaseTicketContext validate(PurchaseTicketRequest request) {
        PurchaseTicketContext context = new PurchaseTicketContext(request);
        responsibilityChain.execute(context);
        return context;
    }
}
