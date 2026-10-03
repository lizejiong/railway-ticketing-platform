package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.ticket.dao.entity.TrainDO;
import com.lzj.railway.ticket.dao.mapper.TrainMapper;
import com.lzj.railway.ticket.dto.request.PurchaseTicketPassengerRequest;
import com.lzj.railway.ticket.dto.request.PurchaseTicketRequest;
import com.lzj.railway.ticket.service.query.StationRegionCache;
import com.lzj.railway.ticket.service.query.TicketStationCacheDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 提交购票责任链测试。
 */
class PurchaseTicketValidationChainTest {

    /**
     * 合法请求应解析车站名称并生成连续区间。
     */
    @Test
    void shouldBuildPurchaseContext() {
        StationRegionCache stationRegionCache = mock(StationRegionCache.class);
        TrainMapper trainMapper = mock(TrainMapper.class);
        TrainRouteService trainRouteService = mock(TrainRouteService.class);
        when(stationRegionCache.getStations("VNP", "NKH")).thenReturn(Map.of(
                "VNP", new TicketStationCacheDTO("VNP", "北京南", "北京"),
                "NKH", new TicketStationCacheDTO("NKH", "南京南", "南京")
        ));
        TrainDO train = new TrainDO();
        train.setId(3L);
        train.setDelFlag(0);
        train.setSaleStatus(0);
        when(trainMapper.selectById(3L)).thenReturn(train);
        when(trainRouteService.listRouteSegments(3L, "北京南", "南京南"))
                .thenReturn(List.of(new TrainRouteSegment("北京南", "南京南")));

        PurchaseTicketValidationChain chain = chain(
                stationRegionCache, trainMapper, trainRouteService);

        PurchaseTicketContext context = chain.validate(request());

        assertThat(context.getDepartureName()).isEqualTo("北京南");
        assertThat(context.getArrivalName()).isEqualTo("南京南");
        assertThat(context.getRouteSegments()).containsExactly(
                new TrainRouteSegment("北京南", "南京南"));
    }

    /**
     * 缺少乘车人时应在访问缓存和数据库前被参数校验拒绝。
     */
    @Test
    void shouldRejectInvalidRequestBeforeExternalLookup() {
        StationRegionCache stationRegionCache = mock(StationRegionCache.class);
        TrainMapper trainMapper = mock(TrainMapper.class);
        TrainRouteService trainRouteService = mock(TrainRouteService.class);
        PurchaseTicketValidationChain chain = chain(
                stationRegionCache, trainMapper, trainRouteService);

        PurchaseTicketRequest invalidRequest = new PurchaseTicketRequest(
                3L, "VNP", "NKH", List.of(), List.of());

        assertThatThrownBy(() -> chain.validate(invalidRequest))
                .isInstanceOf(ClientException.class)
                .hasFieldOrPropertyWithValue("errorCode", "T000004");
        verifyNoInteractions(stationRegionCache, trainMapper, trainRouteService);
    }

    /**
     * 组装与 Spring 容器中相同顺序的购票责任链。
     *
     * @param stationRegionCache 车站缓存
     * @param trainMapper 列车数据访问接口
     * @param trainRouteService 行程区间服务
     * @return 购票责任链
     */
    private PurchaseTicketValidationChain chain(
            StationRegionCache stationRegionCache,
            TrainMapper trainMapper,
            TrainRouteService trainRouteService) {
        return new PurchaseTicketValidationChain(List.of(
                new PurchaseTicketBusinessValidationHandler(trainMapper, trainRouteService),
                new PurchaseTicketStationResolveHandler(stationRegionCache),
                new PurchaseTicketParameterValidationHandler(
                        Validation.buildDefaultValidatorFactory().getValidator())
        ));
    }

    /**
     * 创建一份合法的最小购票请求。
     *
     * @return 购票请求
     */
    private PurchaseTicketRequest request() {
        return new PurchaseTicketRequest(
                3L,
                "VNP",
                "NKH",
                List.of(new PurchaseTicketPassengerRequest(1L, 1)),
                List.of());
    }
}
