package com.lzj.railway.ticket.service.query;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 服务启动完成后预热票务查询读模型。
 */
@Component
@RequiredArgsConstructor
public class TicketQueryCacheInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final StationRegionCache stationRegionCache;
    private final TicketQueryReadModel ticketQueryReadModel;

    /**
     * 预热车站、区间、票价与余票缓存。
     *
     * @param event Spring 应用就绪事件
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        stationRegionCache.warmUp();
        ticketQueryReadModel.warmUpRoutes();
    }
}
