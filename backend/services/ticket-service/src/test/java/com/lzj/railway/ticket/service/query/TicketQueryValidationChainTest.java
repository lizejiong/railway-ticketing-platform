package com.lzj.railway.ticket.service.query;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.ticket.dto.request.TicketQueryRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 车票查询责任链测试。
 */
class TicketQueryValidationChainTest {

    /**
     * 合法站点编码应转换为后续读模型可直接使用的查询上下文。
     */
    @Test
    void shouldBuildQueryContextFromStationCache() {
        StationRegionCache stationRegionCache = mock(StationRegionCache.class);
        when(stationRegionCache.getStations("VNP", "NKH")).thenReturn(Map.of(
                "VNP", new TicketStationCacheDTO("VNP", "北京南", "北京"),
                "NKH", new TicketStationCacheDTO("NKH", "南京南", "南京")
        ));
        TicketQueryValidationChain chain = new TicketQueryValidationChain(
                List.of(
                        new TicketQueryParameterValidationHandler(
                                Validation.buildDefaultValidatorFactory().getValidator()),
                        new TicketQueryBusinessValidationHandler()
                ),
                stationRegionCache
        );

        TicketQueryContext context = chain.validate(new TicketQueryRequest(
                "VNP", "NKH", LocalDate.now().plusDays(1)));

        assertThat(context.fromStationName()).isEqualTo("北京南");
        assertThat(context.toRegion()).isEqualTo("南京");
    }

    /**
     * 过去日期应在访问 Redis 站点缓存前被责任链拒绝。
     */
    @Test
    void shouldRejectPastDateBeforeStationCacheLookup() {
        StationRegionCache stationRegionCache = mock(StationRegionCache.class);
        TicketQueryValidationChain chain = new TicketQueryValidationChain(
                List.of(
                        new TicketQueryParameterValidationHandler(
                                Validation.buildDefaultValidatorFactory().getValidator()),
                        new TicketQueryBusinessValidationHandler()
                ),
                stationRegionCache
        );

        assertThatThrownBy(() -> chain.validate(new TicketQueryRequest(
                "VNP", "NKH", LocalDate.now().minusDays(1))))
                .isInstanceOf(ClientException.class)
                .hasFieldOrPropertyWithValue("errorCode", "T000002");
        verifyNoInteractions(stationRegionCache);
    }
}
