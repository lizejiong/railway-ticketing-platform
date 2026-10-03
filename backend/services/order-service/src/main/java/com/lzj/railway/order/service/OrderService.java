package com.lzj.railway.order.service;

import com.lzj.railway.order.dto.request.CancelTicketOrderRequest;
import com.lzj.railway.order.dto.request.TicketOrderCreateRequest;
import com.lzj.railway.order.dto.response.TicketOrderResponse;

/** 订单主状态、明细和乘车人关系的事务编排入口。 */
public interface OrderService {
    /** 创建待支付订单并写入全部快照。 */
    String createTicketOrder(TicketOrderCreateRequest request);

    /** 查询订单及其乘车人明细，同时校验订单归属。 */
    TicketOrderResponse queryTicketOrder(String orderSn, String username);

    /** 将待支付订单原子关闭。 */
    void cancelTicketOrder(CancelTicketOrderRequest request);
}
