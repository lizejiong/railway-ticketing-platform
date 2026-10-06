package com.lzj.railway.ticket.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzj.railway.framework.convention.exception.ServiceException;
import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.common.constant.TicketStatus;
import com.lzj.railway.ticket.dao.entity.TicketDO;
import com.lzj.railway.ticket.dao.mapper.SeatMapper;
import com.lzj.railway.ticket.dao.mapper.TicketMapper;
import com.lzj.railway.ticket.remote.TicketOrderRemoteService;
import com.lzj.railway.ticket.remote.dto.TicketOrderItemRemoteResponse;
import com.lzj.railway.ticket.remote.dto.TicketOrderRemoteResponse;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 消费支付成功消息，将区间实体座位由锁定状态最终置为已售出。 */
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "railway-pay-result", consumerGroup = "ticket-service-payment-consumer")
public class PaySuccessTicketConsumer implements RocketMQListener<String> {
    private final ObjectMapper objectMapper;
    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final SeatMapper seatMapper;
    private final TicketMapper ticketMapper;

    /** 同一支付消息重复到达时，已售出的座位不满足 LOCKED 条件，因此更新天然幂等。 */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void onMessage(String message) {
        try {
            PaySuccessMessage event = objectMapper.readValue(message, PaySuccessMessage.class);
            Result<TicketOrderRemoteResponse> result = ticketOrderRemoteService.queryInternal(event.orderSn());
            if (result == null || !result.isSuccess() || result.getData() == null) {
                throw new ServiceException(TicketErrorCode.ORDER_SERVICE_FAILED);
            }
            TicketOrderRemoteResponse order = result.getData();
            for (TicketOrderItemRemoteResponse item : order.passengerDetails()) {
                // 只有车票从待支付实际推进为已支付，才结算对应区间座位；重复和乱序消息均不会重复卖座。
                int updatedTickets = ticketMapper.update(null, new LambdaUpdateWrapper<TicketDO>()
                        .eq(TicketDO::getOrderSn, order.orderSn())
                        .eq(TicketDO::getTrainId, order.trainId())
                        .eq(TicketDO::getCarriageNumber, item.carriageNumber())
                        .eq(TicketDO::getSeatNumber, item.seatNumber())
                        .eq(TicketDO::getTicketStatus, TicketStatus.UNPAID)
                        .set(TicketDO::getTicketStatus, TicketStatus.PAID)
                        .set(TicketDO::getUpdateTime, LocalDateTime.now()));
                if (updatedTickets == 1) {
                    seatMapper.sellSeat(order.trainId(), item.carriageNumber(), item.seatNumber(),
                            order.departure(), order.arrival());
                }
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("处理支付成功票务消息失败", exception);
        }
    }
}
