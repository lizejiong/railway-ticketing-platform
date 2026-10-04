package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.dao.entity.SeatDO;
import com.lzj.railway.ticket.dao.mapper.SeatMapper;
import com.lzj.railway.ticket.dto.request.PurchaseTicketPassengerRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * @param chooseSeats 用户期望的车厢内座位号；为空时自动分配
     * @return 已分配座位
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public List<AllocatedSeat> allocateAndLock(
            Long trainId,
            List<PurchaseTicketPassengerRequest> passengers,
            List<TrainRouteSegment> affectedSegments,
            List<String> chooseSeats) {
        if (CollectionUtils.isEmpty(chooseSeats)) {
            return allocateAutomatically(trainId, passengers, affectedSegments);
        }
        return allocateChosenSeats(trainId, passengers, affectedSegments, chooseSeats);
    }

    /**
     * 未指定座位时，按席别和车厢、座位号顺序自动分配可用座位。
     */
    private List<AllocatedSeat> allocateAutomatically(
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
     * 按用户指定的车厢内座位号选座。
     *
     * <p>当前数据模型中的 {@code seatNumber} 在不同车厢可重复，因此需要在同一车厢中找到一整套指定位置。
     * 指定选座仅支持所有乘车人选择同一席别，避免一份未携带席别信息的座位号列表产生歧义。</p>
     */
    private List<AllocatedSeat> allocateChosenSeats(
            Long trainId,
            List<PurchaseTicketPassengerRequest> passengers,
            List<TrainRouteSegment> affectedSegments,
            List<String> chooseSeats) {
        if (chooseSeats.size() != passengers.size()
                || passengers.stream().map(PurchaseTicketPassengerRequest::seatType).distinct().count() != 1
                || chooseSeats.stream().anyMatch(seatNumber -> !StringUtils.hasText(seatNumber))) {
            throw new ClientException(TicketErrorCode.PURCHASE_PARAMETER_INVALID);
        }
        Set<String> uniqueSeatNumbers = Set.copyOf(chooseSeats);
        if (uniqueSeatNumbers.size() != chooseSeats.size()) {
            throw new ClientException(TicketErrorCode.PURCHASE_PARAMETER_INVALID);
        }
        Integer seatType = passengers.get(0).seatType();
        List<SeatDO> candidates = seatMapper.selectAvailableChosenSeatsForSegments(
                trainId, seatType, affectedSegments, chooseSeats);
        Map<String, Map<String, SeatDO>> seatsByCarriage = candidates.stream().collect(Collectors.groupingBy(
                SeatDO::getCarriageNumber,
                LinkedHashMap::new,
                Collectors.toMap(SeatDO::getSeatNumber, seat -> seat, (left, right) -> left, LinkedHashMap::new)));
        Map<String, SeatDO> selectedSeats = seatsByCarriage.values().stream()
                .filter(seatMap -> seatMap.keySet().containsAll(uniqueSeatNumbers))
                .findFirst()
                .orElseThrow(() -> new ClientException(TicketErrorCode.TICKET_SOLD_OUT));
        List<AllocatedSeat> result = new ArrayList<>(passengers.size());
        for (int index = 0; index < passengers.size(); index++) {
            PurchaseTicketPassengerRequest passenger = passengers.get(index);
            SeatDO seat = selectedSeats.get(chooseSeats.get(index));
            lockAllAffectedSegments(trainId, seat, affectedSegments);
            result.add(new AllocatedSeat(passenger.passengerId(), seat.getSeatType(),
                    seat.getCarriageNumber(), seat.getSeatNumber(), seat.getPrice()));
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
