package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.designpattern.chain.ChainDecision;
import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.service.query.StationRegionCache;
import com.lzj.railway.ticket.service.query.TicketStationCacheDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 将客户端传入的车站编码解析为票务表使用的车站名称。
 */
@Component
@RequiredArgsConstructor
public class PurchaseTicketStationResolveHandler implements ChainHandler<PurchaseTicketContext> {

    private final StationRegionCache stationRegionCache;

    /**
     * 读取车站缓存并保存名称，后续路径、座位和票价查询不再使用不稳定的展示输入。
     *
     * @param context 已完成参数校验的购票上下文
     * @return 继续责任链
     */
    @Override
    public ChainDecision handle(PurchaseTicketContext context) {
        Map<String, TicketStationCacheDTO> stations = stationRegionCache.getStations(
                context.getRequest().departure(), context.getRequest().arrival());
        TicketStationCacheDTO departure = stations.get(context.getRequest().departure());
        TicketStationCacheDTO arrival = stations.get(context.getRequest().arrival());
        if (departure == null || arrival == null
                || departure.code().equalsIgnoreCase(arrival.code())) {
            throw new ClientException(TicketErrorCode.JOURNEY_INVALID);
        }
        context.setDepartureName(departure.name());
        context.setArrivalName(arrival.name());
        return ChainDecision.CONTINUE;
    }

    /**
     * 车站编码解析在参数校验之后、业务校验之前执行。
     *
     * @return 处理器顺序
     */
    @Override
    public int order() {
        return 10;
    }
}
