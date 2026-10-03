package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.designpattern.chain.ChainDecision;
import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.dao.entity.TrainDO;
import com.lzj.railway.ticket.dao.mapper.TrainMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 校验列车可售状态并计算购票行程的连续区间。
 */
@Component
@RequiredArgsConstructor
public class PurchaseTicketBusinessValidationHandler implements ChainHandler<PurchaseTicketContext> {

    private final TrainMapper trainMapper;
    private final TrainRouteService trainRouteService;

    /**
     * 确认列车存在且可售，并生成后续锁座必须使用的连续区间。
     *
     * @param context 已完成参数与车站解析的购票上下文
     * @return 继续责任链
     */
    @Override
    public ChainDecision handle(PurchaseTicketContext context) {
        TrainDO train = trainMapper.selectById(context.getRequest().trainId());
        if (train == null || !Integer.valueOf(0).equals(train.getDelFlag())) {
            throw new ClientException(TicketErrorCode.TRAIN_NOT_FOUND);
        }
        if (!Integer.valueOf(0).equals(train.getSaleStatus())) {
            throw new ClientException(TicketErrorCode.TRAIN_NOT_FOR_SALE);
        }
        context.setRouteSegments(trainRouteService.listRouteSegments(
                train.getId(), context.getDepartureName(), context.getArrivalName()));
        return ChainDecision.CONTINUE;
    }

    /**
     * 列车和行程业务校验在车站编码解析后执行。
     *
     * @return 处理器顺序
     */
    @Override
    public int order() {
        return 20;
    }
}
