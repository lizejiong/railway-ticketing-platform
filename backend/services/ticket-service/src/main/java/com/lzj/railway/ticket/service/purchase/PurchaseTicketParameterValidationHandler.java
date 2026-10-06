package com.lzj.railway.ticket.service.purchase;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.designpattern.chain.ChainDecision;
import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.ticket.common.errorcode.TicketErrorCode;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 校验购票请求的字段格式与基础约束。
 */
@Component
@RequiredArgsConstructor
public class PurchaseTicketParameterValidationHandler implements ChainHandler<PurchaseTicketContext> {

    private final Validator validator;

    /**
     * 在访问缓存或数据库前完成 Bean Validation 校验。
     *
     * @param context 购票上下文
     * @return 继续责任链
     */
    @Override
    public ChainDecision handle(PurchaseTicketContext context) {
        if (context == null || context.getRequest() == null
                || !validator.validate(context.getRequest()).isEmpty()) {
            throw new ClientException(TicketErrorCode.PURCHASE_PARAMETER_INVALID);
        }
        return ChainDecision.CONTINUE;
    }

    /**
     * 参数校验必须最先执行。
     *
     * @return 处理器顺序
     */
    @Override
    public int order() {
        return 0;
    }
}
