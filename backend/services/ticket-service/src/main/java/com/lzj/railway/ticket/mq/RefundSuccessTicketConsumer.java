package com.lzj.railway.ticket.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzj.railway.ticket.dao.mapper.SeatMapper;
import com.lzj.railway.ticket.service.purchase.TicketAvailabilityTokenBucket;
import com.lzj.railway.ticket.service.purchase.TrainRouteSegment;
import com.lzj.railway.ticket.service.purchase.TrainRouteService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

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
    private final TrainRouteService trainRouteService;
    private final TicketAvailabilityTokenBucket tokenBucket;

    @Override public void onMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            Long trainId = root.path("trainId").asLong();
            String departure = root.path("departure").asText();
            String arrival = root.path("arrival").asText();
            List<TrainRouteSegment> segments = trainRouteService.listAffectedSaleSegments(trainId, departure, arrival);
            java.util.Map<Integer, Long> counts = new java.util.HashMap<>();
            for (JsonNode item : root.path("items")) {
                int seatType = item.path("seatType").asInt();
                for (TrainRouteSegment segment : segments) seatMapper.refundSeat(trainId,
                        item.path("carriageNumber").asText(), item.path("seatNumber").asText(),
                        segment.departure(), segment.arrival());
                counts.merge(seatType, 1L, Long::sum);
            }
            tokenBucket.rollbackInBucket(trainId, segments, counts);
        } catch (Exception exception) { throw new IllegalStateException("处理退款票务消息失败", exception); }
    }
}
