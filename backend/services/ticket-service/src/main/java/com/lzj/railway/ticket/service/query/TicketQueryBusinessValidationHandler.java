package com.lzj.railway.ticket.service.query;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.designpattern.chain.ChainDecision;
import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.dto.request.TicketQueryRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 校验票务查询的基础业务规则。
 */
@Component
public class TicketQueryBusinessValidationHandler implements ChainHandler<TicketQueryRequest> {

    /**
     * 拒绝过去日期和相同出到站的无效查询。
     *
     * @param request 已完成参数格式校验的请求
     * @return 继续执行责任链
     */
    @Override
    public ChainDecision handle(TicketQueryRequest request) {
        if (request.departureDate().isBefore(LocalDate.now())) {
            throw new ClientException(TicketErrorCode.DEPARTURE_DATE_INVALID);
        }
        if (request.fromStation().equalsIgnoreCase(request.toStation())) {
            throw new ClientException(TicketErrorCode.QUERY_PARAMETER_INVALID);
        }
        return ChainDecision.CONTINUE;
    }

    /**
     * 业务规则在参数格式校验后执行。
     *
     * @return 处理器顺序
     */
    @Override
    public int order() {
        return 10;
    }
}
