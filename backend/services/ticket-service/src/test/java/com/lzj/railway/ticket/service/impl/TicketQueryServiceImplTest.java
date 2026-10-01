package com.lzj.railway.ticket.service.impl;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 票务查询服务测试。
 */
@ExtendWith(MockitoExtension.class)
class TicketQueryServiceImplTest {

    @Mock
    private TrainStationRelationMapper trainStationRelationMapper;
    @Mock
    private TrainMapper trainMapper;
    @Mock
    private TrainStationPriceMapper trainStationPriceMapper;
    @Mock
    private SeatMapper seatMapper;

    private TicketQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketQueryServiceImpl(
                trainStationRelationMapper, trainMapper, trainStationPriceMapper, seatMapper);
    }

    @Test
    void shouldAssembleTrainSeatPriceAndRemainingTickets() {
        when(trainStationRelationMapper.selectList(any())).thenReturn(List.of(relation()));
        when(trainMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(train()));
        when(trainStationPriceMapper.selectList(any())).thenReturn(List.of(price(0, 55300)));
        when(seatMapper.countAvailableSeatsByTrainIds(any(), eq("北京南"), eq("上海虹桥")))
                .thenReturn(List.of(new SeatRemainingDTO(1L, 0, 20)));

        List<TicketQueryResponse> result = service.query(new TicketQueryRequest(
                "北京南", "上海虹桥", LocalDate.now().plusDays(1)));

        assertEquals(1, result.size());
        assertEquals("G1", result.get(0).trainNumber());
        assertEquals(268L, result.get(0).durationMinutes());
        assertEquals(List.of(new SeatClassResponse(0, 20, new BigDecimal("553.00"))),
                result.get(0).seatClasses());
    }

    @Test
    void shouldRejectPastDepartureDate() {
        assertThrows(RuntimeException.class, () -> service.query(new TicketQueryRequest(
                "北京南", "上海虹桥", LocalDate.now().minusDays(1))));
    }

    @Test
    void shouldRejectSameDepartureAndArrival() {
        assertThrows(RuntimeException.class, () -> service.query(new TicketQueryRequest(
                "北京南", "北京南", LocalDate.now())));
    }

    private TrainStationRelationDO relation() {
        TrainStationRelationDO relation = new TrainStationRelationDO();
        relation.setTrainId(1L);
        relation.setDeparture("北京南");
        relation.setArrival("上海虹桥");
        relation.setDepartureTime(LocalDateTime.of(2026, 10, 2, 7, 0));
        relation.setArrivalTime(LocalDateTime.of(2026, 10, 2, 11, 28));
        relation.setDepartureFlag(true);
        relation.setArrivalFlag(true);
        return relation;
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

    private TrainStationPriceDO price(Integer seatType, Integer price) {
        TrainStationPriceDO priceDO = new TrainStationPriceDO();
        priceDO.setTrainId(1L);
        priceDO.setDeparture("北京南");
        priceDO.setArrival("上海虹桥");
        priceDO.setSeatType(seatType);
        priceDO.setPrice(price);
        return priceDO;
    }
}
