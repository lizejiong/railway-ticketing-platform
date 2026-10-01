package com.lzj.railway.ticket.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.dao.entity.TrainDO;
import com.lzj.railway.ticket.dao.entity.TrainStationPriceDO;
import com.lzj.railway.ticket.dao.entity.TrainStationRelationDO;
import com.lzj.railway.ticket.dao.mapper.SeatMapper;
import com.lzj.railway.ticket.dao.mapper.TrainMapper;
import com.lzj.railway.ticket.dao.mapper.TrainStationPriceMapper;
import com.lzj.railway.ticket.dao.mapper.TrainStationRelationMapper;
import com.lzj.railway.ticket.dao.mapper.dto.SeatRemainingDTO;
import com.lzj.railway.ticket.dto.request.TicketQueryRequest;
import com.lzj.railway.ticket.dto.response.SeatClassResponse;
import com.lzj.railway.ticket.dto.response.TicketQueryResponse;
import com.lzj.railway.ticket.service.TicketQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基于关系表、票价表和座位表的车次查询实现。
 */
@Service
@RequiredArgsConstructor
public class TicketQueryServiceImpl implements TicketQueryService {

    private static final int AVAILABLE_SALE_STATUS = 0;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final TrainStationRelationMapper trainStationRelationMapper;
    private final TrainMapper trainMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final SeatMapper seatMapper;

    /**
     * 查询当前区间下处于可售状态的车次，并组合各席别的票价与余票。
     *
     * @param request 区间与乘车日期条件
     * @return 车次查询结果
     */
    @Override
    public List<TicketQueryResponse> query(TicketQueryRequest request) {
        validateRequest(request);
        List<TrainStationRelationDO> relations = trainStationRelationMapper.selectList(
                Wrappers.lambdaQuery(TrainStationRelationDO.class)
                        .eq(TrainStationRelationDO::getDeparture, request.departure())
                        .eq(TrainStationRelationDO::getArrival, request.arrival())
                        .eq(TrainStationRelationDO::getDelFlag, 0));
        if (relations.isEmpty()) {
            return List.of();
        }

        List<Long> trainIds = relations.stream().map(TrainStationRelationDO::getTrainId).distinct().toList();
        Map<Long, TrainDO> availableTrains = trainMapper.selectBatchIds(trainIds).stream()
                .filter(train -> Objects.equals(train.getSaleStatus(), AVAILABLE_SALE_STATUS))
                .collect(Collectors.toMap(TrainDO::getId, Function.identity()));
        if (availableTrains.isEmpty()) {
            return List.of();
        }

        List<Long> availableTrainIds = availableTrains.keySet().stream().toList();
        Map<TrainSeatKey, Integer> remainingTickets = buildRemainingTicketMap(
                seatMapper.countAvailableSeatsByTrainIds(availableTrainIds, request.departure(), request.arrival()));
        Map<Long, List<TrainStationPriceDO>> pricesByTrainId = trainStationPriceMapper.selectList(
                        Wrappers.lambdaQuery(TrainStationPriceDO.class)
                                .in(TrainStationPriceDO::getTrainId, availableTrainIds)
                                .eq(TrainStationPriceDO::getDeparture, request.departure())
                                .eq(TrainStationPriceDO::getArrival, request.arrival())
                                .eq(TrainStationPriceDO::getDelFlag, 0))
                .stream()
                .collect(Collectors.groupingBy(TrainStationPriceDO::getTrainId));

        return relations.stream()
                .filter(relation -> availableTrains.containsKey(relation.getTrainId()))
                .sorted(Comparator.comparing(TrainStationRelationDO::getDepartureTime))
                .map(relation -> buildResponse(relation, availableTrains.get(relation.getTrainId()),
                        pricesByTrainId.getOrDefault(relation.getTrainId(), List.of()), remainingTickets))
                .toList();
    }

    /**
     * 校验查询参数的业务约束。
     *
     * @param request 查询条件
     */
    private void validateRequest(TicketQueryRequest request) {
        if (request.departure().equals(request.arrival())) {
            throw new ClientException(TicketErrorCode.QUERY_PARAMETER_INVALID);
        }
        if (request.departureDate().isBefore(LocalDate.now())) {
            throw new ClientException(TicketErrorCode.DEPARTURE_DATE_INVALID);
        }
    }

    /**
     * 将余票聚合结果转为按车次和席别快速检索的映射。
     *
     * @param remainingSeatRows 数据库聚合结果
     * @return 余票映射
     */
    private Map<TrainSeatKey, Integer> buildRemainingTicketMap(List<SeatRemainingDTO> remainingSeatRows) {
        Map<TrainSeatKey, Integer> result = new HashMap<>();
        remainingSeatRows.forEach(row -> result.put(
                new TrainSeatKey(row.trainId(), row.seatType()), row.remainingTickets()));
        return result;
    }

    /**
     * 组合一条车次区间与其全部定价席别。
     *
     * @param relation 列车区间关系
     * @param train 列车基础信息
     * @param prices 当前区间票价
     * @param remainingTickets 当前区间余票映射
     * @return 对外查询响应
     */
    private TicketQueryResponse buildResponse(TrainStationRelationDO relation, TrainDO train,
                                              List<TrainStationPriceDO> prices,
                                              Map<TrainSeatKey, Integer> remainingTickets) {
        List<SeatClassResponse> seatClasses = prices.stream()
                .sorted(Comparator.comparing(TrainStationPriceDO::getSeatType))
                .map(price -> new SeatClassResponse(price.getSeatType(),
                        remainingTickets.getOrDefault(new TrainSeatKey(train.getId(), price.getSeatType()), 0),
                        BigDecimal.valueOf(price.getPrice(), 2)))
                .toList();
        return new TicketQueryResponse(train.getId(), train.getTrainNumber(), relation.getDeparture(), relation.getArrival(),
                formatTime(relation.getDepartureTime()), formatTime(relation.getArrivalTime()),
                Duration.between(relation.getDepartureTime(), relation.getArrivalTime()).toMinutes(),
                relation.getDepartureFlag(), relation.getArrivalFlag(), train.getTrainType(), train.getTrainBrand(),
                train.getSaleStatus(), seatClasses);
    }

    /**
     * 将数据库日期时间格式化为前端展示所需的时分格式。
     *
     * @param time 日期时间
     * @return HH:mm 格式时刻
     */
    private String formatTime(LocalDateTime time) {
        return TIME_FORMATTER.format(time);
    }

    /**
     * 余票映射的复合键。
     *
     * @param trainId 列车主键
     * @param seatType 席别类型
     */
    private record TrainSeatKey(Long trainId, Integer seatType) {
    }
}
