package com.lzj.railway.ticket.service.impl;

import com.lzj.railway.ticket.dao.entity.TrainDO;
import com.lzj.railway.ticket.dao.entity.TrainStationPriceDO;
import com.lzj.railway.ticket.dto.request.TicketQueryRequest;
import com.lzj.railway.ticket.dto.response.SeatClassResponse;
import com.lzj.railway.ticket.dto.response.TicketQueryResponse;
import com.lzj.railway.ticket.service.TicketQueryService;
import com.lzj.railway.ticket.service.query.TicketQueryContext;
import com.lzj.railway.ticket.service.query.TicketQueryReadModel;
import com.lzj.railway.ticket.service.query.TicketQueryValidationChain;
import com.lzj.railway.ticket.service.query.TicketRouteCacheDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 Redis 读模型的车次与余票查询实现。
 */
@Service
@RequiredArgsConstructor
public class TicketQueryServiceImpl implements TicketQueryService {

    private static final int AVAILABLE_SALE_STATUS = 0;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final TicketQueryValidationChain validationChain;
    private final TicketQueryReadModel ticketQueryReadModel;

    /**
     * 查询当前区间下处于可售状态的车次，并批量组合票价和余票。
     *
     * @param request 区间与乘车日期条件
     * @return 车次查询结果
     */
    @Override
    public List<TicketQueryResponse> query(TicketQueryRequest request) {
        TicketQueryContext context = validationChain.validate(request);
        List<TicketRouteCacheDTO> routes = ticketQueryReadModel.findRoutes(
                        context.fromRegion(), context.toRegion()).stream()
                .filter(route -> route.getDeparture().equals(context.fromStationName()))
                .filter(route -> route.getArrival().equals(context.toStationName()))
                .toList();
        if (routes.isEmpty()) {
            return List.of();
        }

        Map<Long, TrainDO> availableTrains = ticketQueryReadModel.findTrains(routes.stream()
                        .map(TicketRouteCacheDTO::getTrainId)
                        .distinct()
                        .toList()).entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue().getSaleStatus(), AVAILABLE_SALE_STATUS))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<TicketRouteCacheDTO> availableRoutes = routes.stream()
                .filter(route -> availableTrains.containsKey(route.getTrainId()))
                .sorted(Comparator.comparing(TicketRouteCacheDTO::getDepartureTime))
                .toList();
        if (availableRoutes.isEmpty()) {
            return List.of();
        }

        Map<Long, List<TrainStationPriceDO>> prices = ticketQueryReadModel
                .findPricesByRoutePipelined(availableRoutes);
        Map<Long, Map<Integer, Integer>> remainingTickets = ticketQueryReadModel
                .findRemainingTicketsByRoutePipelined(availableRoutes);
        return availableRoutes.stream().map(route -> buildResponse(route,
                availableTrains.get(route.getTrainId()),
                prices.getOrDefault(route.getTrainId(), List.of()),
                remainingTickets.getOrDefault(route.getTrainId(), Map.of()))).toList();
    }

    /**
     * 将读模型中的列车、票价和余票组合为对外响应。
     *
     * @param route 列车区间
     * @param train 列车基础信息
     * @param prices 区间票价
     * @param remainingTickets 席别余票
     * @return 对外车次查询响应
     */
    private TicketQueryResponse buildResponse(TicketRouteCacheDTO route, TrainDO train,
                                              List<TrainStationPriceDO> prices,
                                              Map<Integer, Integer> remainingTickets) {
        List<SeatClassResponse> seatClasses = prices.stream()
                .sorted(Comparator.comparing(TrainStationPriceDO::getSeatType))
                .map(price -> new SeatClassResponse(price.getSeatType(),
                        remainingTickets.getOrDefault(price.getSeatType(), 0),
                        BigDecimal.valueOf(price.getPrice(), 2)))
                .toList();
        return new TicketQueryResponse(train.getId(), train.getTrainNumber(), route.getDeparture(), route.getArrival(),
                formatTime(route.getDepartureTime()), formatTime(route.getArrivalTime()),
                Duration.between(route.getDepartureTime(), route.getArrivalTime()).toMinutes(),
                route.getDepartureFlag(), route.getArrivalFlag(), train.getTrainType(), train.getTrainBrand(),
                train.getSaleStatus(), seatClasses);
    }

    /**
     * 将日期时间格式化为前端展示所需的时分格式。
     *
     * @param time 日期时间
     * @return HH:mm 格式时刻
     */
    private String formatTime(LocalDateTime time) {
        return TIME_FORMATTER.format(time);
    }
}
