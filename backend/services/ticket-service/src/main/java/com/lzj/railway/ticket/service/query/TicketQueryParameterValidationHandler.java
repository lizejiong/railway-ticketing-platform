package com.lzj.railway.ticket.service.query;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.designpattern.chain.ChainDecision;
import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import com.lzj.railway.ticket.dto.request.TicketQueryRequest;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 执行票务查询请求的 Bean Validation 参数校验。
 */
@Component
@RequiredArgsConstructor
public class TicketQueryParameterValidationHandler implements ChainHandler<TicketQueryRequest> {

    private final Validator validator;

    /**
     * 校验站点编码和乘车日期是否满足 DTO 声明的约束。
     *
     * @param request 票务查询请求
     * @return 继续执行责任链
     */
    @Override
    public ChainDecision handle(TicketQueryRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ClientException(TicketErrorCode.QUERY_PARAMETER_INVALID);
        }
        return ChainDecision.CONTINUE;
    }

    /**
     * 参数校验必须优先执行，避免无效请求访问缓存或数据库。
     *
     * @return 处理器顺序
     */
    @Override
    public int order() {
        return 0;
    }
}
