package com.lzj.railway.ticket.service.impl;

import com.lzj.railway.ticket.dao.entity.TrainDO;
import com.lzj.railway.ticket.dao.entity.TrainStationPriceDO;
import com.lzj.railway.ticket.dto.request.TicketQueryRequest;
import com.lzj.railway.ticket.dto.response.SeatClassResponse;
import com.lzj.railway.ticket.dto.response.TicketQueryResponse;
import com.lzj.railway.ticket.service.query.TicketQueryContext;
import com.lzj.railway.ticket.service.query.TicketQueryReadModel;
import com.lzj.railway.ticket.service.query.TicketQueryValidationChain;
import com.lzj.railway.ticket.service.query.TicketRouteCacheDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 基于 Redis 读模型的票务查询服务测试。
 */
class TicketQueryServiceImplTest {

    private TicketQueryValidationChain validationChain;
    private TicketQueryReadModel ticketQueryReadModel;
    private TicketQueryServiceImpl service;

    /**
     * 初始化读模型相关依赖。
     */
    @BeforeEach
    void setUp() {
        validationChain = mock(TicketQueryValidationChain.class);
        ticketQueryReadModel = mock(TicketQueryReadModel.class);
        service = new TicketQueryServiceImpl(validationChain, ticketQueryReadModel);
    }

    /**
     * 应组合缓存区间、批量票价和批量余票为查询响应。
     */
    @Test
    void shouldAssembleTrainSeatPriceAndRemainingTickets() {
        TicketQueryRequest request = new TicketQueryRequest("VNP", "NKH", LocalDate.now().plusDays(1));
        TicketRouteCacheDTO route = route();
        when(validationChain.validate(request)).thenReturn(new TicketQueryContext(
                "VNP", "NKH", "北京南", "南京南", "北京", "南京"));
        when(ticketQueryReadModel.findRoutes("北京", "南京")).thenReturn(List.of(route));
        when(ticketQueryReadModel.findTrains(List.of(1L))).thenReturn(Map.of(1L, train()));
        when(ticketQueryReadModel.findPricesByRoutePipelined(any())).thenReturn(Map.of(1L, List.of(price())));
        when(ticketQueryReadModel.findRemainingTicketsByRoutePipelined(any())).thenReturn(Map.of(1L, Map.of(0, 20)));

        List<TicketQueryResponse> result = service.query(request);

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.trainNumber()).isEqualTo("G1");
            assertThat(response.durationMinutes()).isEqualTo(180L);
            assertThat(response.seatClasses()).isEqualTo(List.of(
                    new SeatClassResponse(0, 20, new BigDecimal("553.00"))));
        });
    }

    private TicketRouteCacheDTO route() {
        TicketRouteCacheDTO route = new TicketRouteCacheDTO();
        route.setTrainId(1L);
        route.setDeparture("北京南");
        route.setArrival("南京南");
        route.setDepartureFlag(true);
        route.setArrivalFlag(true);
        route.setDepartureTime(LocalDateTime.of(2026, 10, 2, 7, 0));
        route.setArrivalTime(LocalDateTime.of(2026, 10, 2, 10, 0));
        return route;
    }

    private TrainDO train() {
        TrainDO train = new TrainDO();
        train.setId(1L);
        train.setTrainNumber("G1");
        train.setTrainType(0);
        train.setTrainBrand("0,6");
        train.setSaleStatus(0);
        return train;
    }

    private TrainStationPriceDO price() {
        TrainStationPriceDO price = new TrainStationPriceDO();
        price.setTrainId(1L);
        price.setDeparture("北京南");
        price.setArrival("南京南");
        price.setSeatType(0);
        price.setPrice(55300);
        return price;
    }
}
