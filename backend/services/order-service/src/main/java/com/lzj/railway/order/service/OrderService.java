package com.lzj.railway.order.service;

import com.lzj.railway.order.dto.request.CancelTicketOrderRequest;
import com.lzj.railway.order.dto.request.TicketOrderCreateRequest;
import com.lzj.railway.order.dto.response.TicketOrderResponse;

import java.time.LocalDateTime;

/** 订单主状态、明细和乘车人关系的事务编排入口。 */
public interface OrderService {
    /** 创建待支付订单并写入全部快照。 */
    String createTicketOrder(TicketOrderCreateRequest request);

    /** 查询订单及其乘车人明细，同时校验订单归属。 */
    TicketOrderResponse queryTicketOrder(String orderSn, String username);

    /** 为内部事件消费者提供订单与座位快照。 */
    TicketOrderResponse queryTicketOrderInternal(String orderSn);

    /** 将待支付订单原子关闭。 */
    void cancelTicketOrder(CancelTicketOrderRequest request);

    /**
     * 关闭超时未支付订单。
     *
     * @param orderSn 订单号
     * @return 仅当本次调用将订单从待支付推进为已关闭时返回 {@code true}
     */
    boolean closeExpiredTicketOrder(String orderSn);

    /** 将指定已支付订单明细标记为已退款。 */
    void refundTicketOrder(String orderSn, java.util.List<Long> orderItemIds);

    /** 消费支付成功事件，将待支付订单及其明细原子推进为已支付。 */
    void confirmTicketOrderPayment(String orderSn, LocalDateTime payTime);
}
