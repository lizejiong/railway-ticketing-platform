package com.lzj.railway.ticket.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lzj.railway.ticket.common.constant.TicketStatus;
import com.lzj.railway.ticket.dao.entity.TicketDO;
import com.lzj.railway.ticket.dao.mapper.SeatMapper;
import com.lzj.railway.ticket.dao.mapper.TicketMapper;
import com.lzj.railway.ticket.service.purchase.TicketAvailabilityTokenBucket;
import com.lzj.railway.ticket.service.purchase.TrainRouteSegment;
import com.lzj.railway.ticket.service.purchase.TrainRouteService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 退款成功后回收对应已售座位，并回补受影响区间的余票令牌。 */
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "railway-refund-result", consumerGroup = "ticket-service-refund-consumer")
public class RefundSuccessTicketConsumer implements RocketMQListener<String> {
    private final ObjectMapper objectMapper;
    private final SeatMapper seatMapper;
    private final TicketMapper ticketMapper;
    private final TrainRouteService trainRouteService;
    private final TicketAvailabilityTokenBucket tokenBucket;

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void onMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String orderSn = root.path("orderSn").asText();
            Long trainId = root.path("trainId").asLong();
            String departure = root.path("departure").asText();
            String arrival = root.path("arrival").asText();
            List<TrainRouteSegment> segments = trainRouteService.listAffectedSaleSegments(trainId, departure, arrival);
            java.util.Map<Integer, Long> counts = new java.util.HashMap<>();
            for (JsonNode item : root.path("items")) {
                int seatType = item.path("seatType").asInt();
                int updatedTickets = ticketMapper.update(null, new LambdaUpdateWrapper<TicketDO>()
                        .eq(TicketDO::getOrderSn, orderSn)
                        .eq(TicketDO::getTrainId, trainId)
                        .eq(TicketDO::getCarriageNumber, item.path("carriageNumber").asText())
                        .eq(TicketDO::getSeatNumber, item.path("seatNumber").asText())
                        .in(TicketDO::getTicketStatus, TicketStatus.UNPAID, TicketStatus.PAID)
                        .set(TicketDO::getTicketStatus, TicketStatus.REFUNDED)
                        .set(TicketDO::getUpdateTime, LocalDateTime.now()));
                if (updatedTickets != 1) {
                    continue;
                }
                for (TrainRouteSegment segment : segments) seatMapper.refundSeat(trainId,
                        item.path("carriageNumber").asText(), item.path("seatNumber").asText(),
                        segment.departure(), segment.arrival());
                // 退款事件可能先于支付事件到达；座位仍处于锁定状态时同样要释放。
                for (TrainRouteSegment segment : segments) seatMapper.unlockSeat(trainId,
                        item.path("carriageNumber").asText(), item.path("seatNumber").asText(),
                        segment.departure(), segment.arrival());
                counts.merge(seatType, 1L, Long::sum);
            }
            tokenBucket.rollbackInBucket(trainId, segments, counts);
        } catch (Exception exception) { throw new IllegalStateException("处理退款票务消息失败", exception); }
    }
}
