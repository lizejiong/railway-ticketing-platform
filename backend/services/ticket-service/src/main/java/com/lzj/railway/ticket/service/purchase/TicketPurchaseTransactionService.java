package com.lzj.railway.ticket.service.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.convention.exception.ServiceException;
import com.lzj.railway.ticket.common.constant.TicketStatus;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.dao.entity.TicketDO;
import com.lzj.railway.ticket.dao.entity.TrainDO;
import com.lzj.railway.ticket.dao.entity.TrainStationPriceDO;
import com.lzj.railway.ticket.dao.entity.TrainStationRelationDO;
import com.lzj.railway.ticket.dao.mapper.TicketMapper;
import com.lzj.railway.ticket.dao.mapper.TrainMapper;
import com.lzj.railway.ticket.dao.mapper.TrainStationPriceMapper;
import com.lzj.railway.ticket.dao.mapper.TrainStationRelationMapper;
import com.lzj.railway.ticket.dto.response.PurchaseTicketResponse;
import com.lzj.railway.ticket.dto.response.PurchasedTicketResponse;
import com.lzj.railway.ticket.remote.TicketOrderRemoteService;
import com.lzj.railway.ticket.remote.dto.PassengerActualRemoteResponse;
import com.lzj.railway.ticket.remote.dto.TicketOrderCreateRemoteRequest;
import com.lzj.railway.ticket.remote.dto.TicketOrderItemCreateRemoteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购票本地事务边界。
 *
 * <p>座位锁定、车票写入和订单远程调用在该事务中串联。订单调用失败会使本地座位状态回滚；
 * Redis 令牌的回补由外层编排器负责，避免它被数据库事务回滚。</p>
 */
@Service
@RequiredArgsConstructor
public class TicketPurchaseTransactionService {
    private final SeatAllocationService seatAllocationService;
    private final TicketMapper ticketMapper;
    private final TrainMapper trainMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final TicketOrderRemoteService ticketOrderRemoteService;

    /** 锁定实体座位、写车票记录并创建待支付订单。 */
    @Transactional(rollbackFor = Throwable.class)
    public PurchaseTicketResponse execute(PurchaseTicketContext context, List<TrainRouteSegment> affectedSegments,
                                          List<PassengerActualRemoteResponse> passengers, Long userId,
                                          String username) {
        TrainDO train = trainMapper.selectById(context.getRequest().trainId());
        if (train == null) {
            throw new ClientException(TicketErrorCode.TRAIN_NOT_FOUND);
        }
        TrainStationRelationDO relation = trainStationRelationMapper.selectOne(
                new LambdaQueryWrapper<TrainStationRelationDO>()
                        .eq(TrainStationRelationDO::getTrainId, train.getId())
                        .eq(TrainStationRelationDO::getDeparture, context.getDepartureName())
                        .eq(TrainStationRelationDO::getArrival, context.getArrivalName())
                        .eq(TrainStationRelationDO::getDelFlag, 0)
                        .last("LIMIT 1"));
        if (relation == null) {
            throw new ClientException(TicketErrorCode.JOURNEY_INVALID);
        }
        Map<Long, PassengerActualRemoteResponse> passengerById = passengers.stream().collect(Collectors.toMap(
                PassengerActualRemoteResponse::id, Function.identity()));
        List<AllocatedSeat> allocatedSeats = seatAllocationService.allocateAndLock(train.getId(),
                context.getRequest().passengers(), affectedSegments);
        LocalDateTime now = LocalDateTime.now();
        for (AllocatedSeat allocatedSeat : allocatedSeats) {
            TicketDO ticket = new TicketDO();
            ticket.setUsername(username);
            ticket.setTrainId(train.getId());
            ticket.setCarriageNumber(allocatedSeat.carriageNumber());
            ticket.setSeatNumber(allocatedSeat.seatNumber());
            ticket.setPassengerId(allocatedSeat.passengerId());
            ticket.setTicketStatus(TicketStatus.UNPAID);
            ticket.setCreateTime(now);
            ticket.setUpdateTime(now);
            ticket.setDelFlag(0);
            if (ticketMapper.insert(ticket) != 1) {
                throw new ServiceException(TicketErrorCode.ORDER_SERVICE_FAILED);
            }
        }
        Map<Integer, Integer> pricesBySeatType = allocatedSeats.stream().map(AllocatedSeat::seatType).distinct()
                .collect(Collectors.toMap(Function.identity(), seatType -> price(train.getId(), context, seatType)));
        List<TicketOrderItemCreateRemoteRequest> items = allocatedSeats.stream().map(allocatedSeat -> {
            PassengerActualRemoteResponse passenger = passengerById.get(allocatedSeat.passengerId());
            return new TicketOrderItemCreateRemoteRequest(allocatedSeat.carriageNumber(), allocatedSeat.seatType(),
                    allocatedSeat.seatNumber(), allocatedSeat.passengerId(), passenger.realName(), passenger.idType(),
                    passenger.idCard(), passenger.phone(), pricesBySeatType.get(allocatedSeat.seatType()),
                    passenger.discountType());
        }).toList();
        TicketOrderCreateRemoteRequest orderRequest = new TicketOrderCreateRemoteRequest(userId, username, train.getId(),
                context.getDepartureName(), context.getArrivalName(), 0, now, relation.getDepartureTime(),
                train.getTrainNumber(), relation.getDepartureTime(), relation.getArrivalTime(), items);
        String orderSn;
        try {
            var result = ticketOrderRemoteService.create(orderRequest);
            if (result == null || !result.isSuccess() || result.getData() == null || result.getData().isBlank()) {
                throw new ServiceException(TicketErrorCode.ORDER_SERVICE_FAILED);
            }
            orderSn = result.getData();
        } catch (ServiceException exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new ServiceException("订单服务调用异常", exception, TicketErrorCode.ORDER_SERVICE_FAILED);
        }
        return new PurchaseTicketResponse(orderSn, allocatedSeats.stream()
                .map(each -> new PurchasedTicketResponse(each.passengerId(), each.carriageNumber(), each.seatType(),
                        each.seatNumber(), pricesBySeatType.get(each.seatType())))
                .toList());
    }

    /** 票价按提交行程和席别读取，避免受影响区间的库存记录价格干扰订单金额。 */
    private Integer price(Long trainId, PurchaseTicketContext context, Integer seatType) {
        TrainStationPriceDO price = trainStationPriceMapper.selectOne(new LambdaQueryWrapper<TrainStationPriceDO>()
                .eq(TrainStationPriceDO::getTrainId, trainId)
                .eq(TrainStationPriceDO::getDeparture, context.getDepartureName())
                .eq(TrainStationPriceDO::getArrival, context.getArrivalName())
                .eq(TrainStationPriceDO::getSeatType, seatType)
                .eq(TrainStationPriceDO::getDelFlag, 0).last("LIMIT 1"));
        if (price == null) {
            throw new ClientException(TicketErrorCode.TICKET_SOLD_OUT);
        }
        return price.getPrice();
    }
}
