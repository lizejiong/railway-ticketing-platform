package com.lzj.railway.ticket.service.query;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.framework.designpattern.chain.ResponsibilityChain;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.dto.request.TicketQueryRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 票务查询责任链入口。
 *
 * <p>先执行不访问外部资源的参数和业务规则，再读取站点缓存，避免无效请求穿透到 Redis。</p>
 */
@Component
public class TicketQueryValidationChain {

    private final ResponsibilityChain<TicketQueryRequest> responsibilityChain;
    private final StationRegionCache stationRegionCache;

    /**
     * 收集并按顺序装配所有票务查询处理器。
     *
     * @param handlers 票务查询责任链处理器
     * @param stationRegionCache 车站与地区映射缓存
     */
    public TicketQueryValidationChain(
            List<ChainHandler<TicketQueryRequest>> handlers,
            StationRegionCache stationRegionCache) {
        this.responsibilityChain = ResponsibilityChain.<TicketQueryRequest>builder()
                .addAll(handlers)
                .build();
        this.stationRegionCache = stationRegionCache;
    }

    /**
     * 校验请求并将站点编码解析为名称和地区，供后续读模型使用。
     *
     * @param request 原始票务查询请求
     * @return 已校验的查询上下文
     */
    public TicketQueryContext validate(TicketQueryRequest request) {
        responsibilityChain.execute(request);
        Map<String, TicketStationCacheDTO> stations = stationRegionCache.getStations(
                request.fromStation(), request.toStation());
        TicketStationCacheDTO fromStation = stations.get(request.fromStation());
        TicketStationCacheDTO toStation = stations.get(request.toStation());
        if (fromStation == null || toStation == null) {
            throw new ClientException(TicketErrorCode.STATION_NOT_FOUND);
        }
        return new TicketQueryContext(
                fromStation.code(), toStation.code(),
                fromStation.name(), toStation.name(),
                fromStation.regionName(), toStation.regionName());
    }
}
