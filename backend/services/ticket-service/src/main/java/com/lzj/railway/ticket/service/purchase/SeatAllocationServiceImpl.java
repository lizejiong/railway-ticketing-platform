package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.dao.entity.SeatDO;
import com.lzj.railway.ticket.dao.mapper.SeatMapper;
import com.lzj.railway.ticket.dto.request.PurchaseTicketPassengerRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 使用数据库条件更新实现最终座位正确性的服务。
 */
@Service
@RequiredArgsConstructor
public class SeatAllocationServiceImpl implements SeatAllocationService {

    private final SeatMapper seatMapper;

    /**
     * 按席别分组选择可用实体座位，并锁定所有受影响区间。
     *
     * <p>候选座位必须在所有受影响区间均为可售，随后每个更新仍带有
     * {@code seat_status = AVAILABLE} 条件，作为并发下的最后一道防线。</p>
     *
     * @param trainId 列车主键
     * @param passengers 乘车人与席别选择
     * @param affectedSegments 需要锁定的全部受影响区间
     * @return 已分配座位
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public List<AllocatedSeat> allocateAndLock(
            Long trainId,
            List<PurchaseTicketPassengerRequest> passengers,
            List<TrainRouteSegment> affectedSegments) {
        Map<Integer, List<PurchaseTicketPassengerRequest>> passengersBySeatType = passengers.stream()
                .collect(Collectors.groupingBy(PurchaseTicketPassengerRequest::seatType));
        List<AllocatedSeat> result = new ArrayList<>();
        for (Map.Entry<Integer, List<PurchaseTicketPassengerRequest>> entry : passengersBySeatType.entrySet()) {
            List<SeatDO> availableSeats = seatMapper.selectAvailableSeatsForSegments(
                    trainId, entry.getKey(), affectedSegments, entry.getValue().size());
            if (availableSeats.size() < entry.getValue().size()) {
                throw new ClientException(TicketErrorCode.TICKET_SOLD_OUT);
            }
            for (int index = 0; index < entry.getValue().size(); index++) {
                PurchaseTicketPassengerRequest passenger = entry.getValue().get(index);
                SeatDO seat = availableSeats.get(index);
                lockAllAffectedSegments(trainId, seat, affectedSegments);
                result.add(new AllocatedSeat(passenger.passengerId(), seat.getSeatType(),
                        seat.getCarriageNumber(), seat.getSeatNumber(), seat.getPrice()));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 释放指定座位对应的所有受影响区间。
     *
     * @param trainId 列车主键
     * @param allocatedSeats 已分配座位
     * @param affectedSegments 需要释放的全部区间
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void unlock(
            Long trainId,
            List<AllocatedSeat> allocatedSeats,
            List<TrainRouteSegment> affectedSegments) {
        for (AllocatedSeat allocatedSeat : allocatedSeats) {
            for (TrainRouteSegment routeSegment : affectedSegments) {
                seatMapper.unlockSeat(trainId, allocatedSeat.carriageNumber(), allocatedSeat.seatNumber(),
                        routeSegment.departure(), routeSegment.arrival());
            }
        }
    }

    /**
     * 对单个实体座位遍历更新全部受影响区间。
     *
     * @param trainId 列车主键
     * @param seat 已选实体座位
     * @param affectedSegments 受影响区间
     */
    private void lockAllAffectedSegments(
            Long trainId, SeatDO seat, List<TrainRouteSegment> affectedSegments) {
        for (TrainRouteSegment routeSegment : affectedSegments) {
            int updatedRows = seatMapper.lockSeat(trainId, seat.getCarriageNumber(), seat.getSeatNumber(),
                    routeSegment.departure(), routeSegment.arrival());
            if (updatedRows != 1) {
                throw new ClientException(TicketErrorCode.SEAT_LOCK_FAILED);
            }
        }
    }
}
