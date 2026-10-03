package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.ticket.dao.entity.TrainStationDO;
import com.lzj.railway.ticket.dao.mapper.TrainStationMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 列车连续区间计算测试。
 */
class TrainRouteServiceTest {

    /**
     * 跨越多个站点的行程应拆分成全部相邻区间。
     */
    @Test
    void shouldResolveAllAdjacentSegments() {
        List<TrainRouteSegment> segments = TrainRouteService.resolveRouteSegments(
                List.of(station("A", "B"), station("B", "C"), station("C", "D")), "A", "C");

        assertThat(segments).containsExactly(
                new TrainRouteSegment("A", "B"),
                new TrainRouteSegment("B", "C"));
    }

    /**
     * 反向行程不能组成连续站序，应被拒绝。
     */
    @Test
    void shouldRejectReverseJourney() {
        assertThatThrownBy(() -> TrainRouteService.resolveRouteSegments(
                List.of(station("A", "B"), station("B", "C")), "C", "A"))
                .isInstanceOf(ClientException.class)
                .hasFieldOrPropertyWithValue("errorCode", "T000007");
    }

    /**
     * 令牌桶初始化需要包含相邻区间与跨站行程的全部组合。
     */
    @Test
    void shouldBuildAllSaleSegmentsFromStationSequence() {
        TrainStationMapper trainStationMapper = mock(TrainStationMapper.class);
        when(trainStationMapper.selectList(any())).thenReturn(List.of(
                station("A", "B"), station("B", "C"), station("C", null)));
        TrainRouteService service = new TrainRouteService(trainStationMapper);

        List<TrainRouteSegment> segments = service.listAllSaleSegments(3L);

        assertThat(segments).containsExactly(
                new TrainRouteSegment("A", "B"),
                new TrainRouteSegment("A", "C"),
                new TrainRouteSegment("B", "C"));
    }

    /**
     * 购买中段行程时，所有与之重叠的售票区间都必须同步扣减。
     */
    @Test
    void shouldBuildAllAffectedSaleSegmentsForPurchase() {
        TrainStationMapper trainStationMapper = mock(TrainStationMapper.class);
        when(trainStationMapper.selectList(any())).thenReturn(List.of(
                station("A", "B"), station("B", "C"), station("C", "D"), station("D", null)));
        TrainRouteService service = new TrainRouteService(trainStationMapper);

        List<TrainRouteSegment> segments = service.listAffectedSaleSegments(3L, "B", "C");

        assertThat(segments).containsExactly(
                new TrainRouteSegment("A", "C"),
                new TrainRouteSegment("A", "D"),
                new TrainRouteSegment("B", "C"),
                new TrainRouteSegment("B", "D"));
    }

    /**
     * 创建用于站序计算的最小站点记录。
     *
     * @param departure 当前站
     * @param arrival 下一站
     * @return 站序记录
     */
    private TrainStationDO station(String departure, String arrival) {
        TrainStationDO station = new TrainStationDO();
        station.setDeparture(departure);
        station.setArrival(arrival);
        return station;
    }
}
