package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.ticket.dao.entity.SeatDO;
import com.lzj.railway.ticket.dao.mapper.SeatMapper;
import com.lzj.railway.ticket.dto.request.PurchaseTicketPassengerRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体座位选择和区间锁座测试。
 */
class SeatAllocationServiceImplTest {

    /**
     * 可用座位足够时，应为每位乘车人分配实体座位并锁定所有受影响区间。
     */
    @Test
    void shouldAllocateSeatsAndLockEveryAffectedSegment() {
        SeatMapper seatMapper = mock(SeatMapper.class);
        SeatAllocationService service = new SeatAllocationServiceImpl(seatMapper);
        List<TrainRouteSegment> affectedSegments = List.of(
                new TrainRouteSegment("A", "C"),
                new TrainRouteSegment("A", "D"),
                new TrainRouteSegment("B", "C"),
                new TrainRouteSegment("B", "D"));
        when(seatMapper.selectAvailableSeatsForSegments(3L, 1, affectedSegments, 2))
                .thenReturn(List.of(seat("01", "01A"), seat("01", "01B")));
        when(seatMapper.lockSeat(eq(3L), any(), any(), any(), any())).thenReturn(1);

        List<AllocatedSeat> allocatedSeats = service.allocateAndLock(3L,
                List.of(new PurchaseTicketPassengerRequest(11L, 1),
                        new PurchaseTicketPassengerRequest(12L, 1)),
                affectedSegments, List.of());

        assertThat(allocatedSeats).containsExactly(
                new AllocatedSeat(11L, 1, "01", "01A", 7500),
                new AllocatedSeat(12L, 1, "01", "01B", 7500));
        verify(seatMapper).lockSeat(3L, "01", "01A", "A", "C");
        verify(seatMapper).lockSeat(3L, "01", "01A", "B", "D");
        verify(seatMapper).lockSeat(3L, "01", "01B", "A", "C");
        verify(seatMapper).lockSeat(3L, "01", "01B", "B", "D");
    }

    /**
     * 所有受影响区间均可用的候选座位不足时，不应进入锁座操作。
     */
    @Test
    void shouldRejectWhenAvailableSeatsAreInsufficient() {
        SeatMapper seatMapper = mock(SeatMapper.class);
        SeatAllocationService service = new SeatAllocationServiceImpl(seatMapper);
        List<TrainRouteSegment> affectedSegments = List.of(new TrainRouteSegment("A", "B"));
        when(seatMapper.selectAvailableSeatsForSegments(3L, 1, affectedSegments, 2))
                .thenReturn(List.of(seat("01", "01A")));

        assertThatThrownBy(() -> service.allocateAndLock(3L,
                List.of(new PurchaseTicketPassengerRequest(11L, 1),
                        new PurchaseTicketPassengerRequest(12L, 1)),
                affectedSegments, List.of()))
                .isInstanceOf(ClientException.class)
                .hasFieldOrPropertyWithValue("errorCode", "T000008");
    }

    /**
     * 数据库条件更新失败时，应向上抛出锁座失败并由事务回滚已有更新。
     */
    @Test
    void shouldRejectWhenConditionalSeatLockFails() {
        SeatMapper seatMapper = mock(SeatMapper.class);
        SeatAllocationService service = new SeatAllocationServiceImpl(seatMapper);
        List<TrainRouteSegment> affectedSegments = List.of(new TrainRouteSegment("A", "B"));
        when(seatMapper.selectAvailableSeatsForSegments(3L, 1, affectedSegments, 1))
                .thenReturn(List.of(seat("01", "01A")));
        when(seatMapper.lockSeat(3L, "01", "01A", "A", "B")).thenReturn(0);

        assertThatThrownBy(() -> service.allocateAndLock(3L,
                List.of(new PurchaseTicketPassengerRequest(11L, 1)), affectedSegments, List.of()))
                .isInstanceOf(ClientException.class)
                .hasFieldOrPropertyWithValue("errorCode", "T000009");
    }

    /** 用户指定的位置在同一车厢内均可用时，应按请求顺序分配对应座位。 */
    @Test
    void shouldAllocateChosenSeatsInSameCarriage() {
        SeatMapper seatMapper = mock(SeatMapper.class);
        SeatAllocationService service = new SeatAllocationServiceImpl(seatMapper);
        List<TrainRouteSegment> affectedSegments = List.of(new TrainRouteSegment("A", "B"));
        List<PurchaseTicketPassengerRequest> passengers = List.of(
                new PurchaseTicketPassengerRequest(11L, 1),
                new PurchaseTicketPassengerRequest(12L, 1));
        when(seatMapper.selectAvailableChosenSeatsForSegments(3L, 1, affectedSegments, List.of("01A", "01C")))
                .thenReturn(List.of(seat("02", "01A"), seat("02", "01C"), seat("03", "01A")));
        when(seatMapper.lockSeat(eq(3L), any(), any(), any(), any())).thenReturn(1);

        List<AllocatedSeat> allocatedSeats = service.allocateAndLock(3L, passengers, affectedSegments,
                List.of("01A", "01C"));

        assertThat(allocatedSeats).containsExactly(
                new AllocatedSeat(11L, 1, "02", "01A", 7500),
                new AllocatedSeat(12L, 1, "02", "01C", 7500));
    }

    /** 指定座位不能凑齐同一车厢时，不能退化为自动选座。 */
    @Test
    void shouldRejectWhenChosenSeatsAreNotAvailableInSameCarriage() {
        SeatMapper seatMapper = mock(SeatMapper.class);
        SeatAllocationService service = new SeatAllocationServiceImpl(seatMapper);
        List<TrainRouteSegment> affectedSegments = List.of(new TrainRouteSegment("A", "B"));
        when(seatMapper.selectAvailableChosenSeatsForSegments(3L, 1, affectedSegments, List.of("01A", "01C")))
                .thenReturn(List.of(seat("02", "01A"), seat("03", "01C")));

        assertThatThrownBy(() -> service.allocateAndLock(3L,
                List.of(new PurchaseTicketPassengerRequest(11L, 1), new PurchaseTicketPassengerRequest(12L, 1)),
                affectedSegments, List.of("01A", "01C")))
                .isInstanceOf(ClientException.class)
                .hasFieldOrPropertyWithValue("errorCode", "T000008");
    }

    /** 指定选座必须与乘车人数一一对应，且仅允许同一席别一起选择。 */
    @Test
    void shouldRejectInvalidChosenSeatRequest() {
        SeatMapper seatMapper = mock(SeatMapper.class);
        SeatAllocationService service = new SeatAllocationServiceImpl(seatMapper);
        List<TrainRouteSegment> affectedSegments = List.of(new TrainRouteSegment("A", "B"));

        assertThatThrownBy(() -> service.allocateAndLock(3L,
                List.of(new PurchaseTicketPassengerRequest(11L, 1), new PurchaseTicketPassengerRequest(12L, 2)),
                affectedSegments, List.of("01A", "01C")))
                .isInstanceOf(ClientException.class)
                .hasFieldOrPropertyWithValue("errorCode", "T000004");
    }

    /**
     * 构造用于选座的最小座位记录。
     *
     * @param carriageNumber 车厢编号
     * @param seatNumber 座位编号
     * @return 座位记录
     */
    private SeatDO seat(String carriageNumber, String seatNumber) {
        SeatDO seat = new SeatDO();
        seat.setCarriageNumber(carriageNumber);
        seat.setSeatNumber(seatNumber);
        seat.setSeatType(1);
        seat.setPrice(7500);
        return seat;
    }
}
