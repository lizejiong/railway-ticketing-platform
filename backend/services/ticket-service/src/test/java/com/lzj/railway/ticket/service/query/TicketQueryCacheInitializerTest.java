package com.lzj.railway.ticket.service.query;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 查询读模型启动预热器测试。
 */
class TicketQueryCacheInitializerTest {

    /**
     * 应在应用就绪时依次预热站点和票务读模型。
     */
    @Test
    void shouldWarmUpStationAndTicketReadModel() {
        StationRegionCache stationRegionCache = mock(StationRegionCache.class);
        TicketQueryReadModel readModel = mock(TicketQueryReadModel.class);
        TicketQueryCacheInitializer initializer = new TicketQueryCacheInitializer(stationRegionCache, readModel);

        initializer.onApplicationEvent(null);

        verify(stationRegionCache).warmUp();
        verify(readModel).warmUpRoutes();
    }
}
