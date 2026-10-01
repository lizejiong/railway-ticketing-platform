package com.lzj.railway.user.service.registration;

import com.lzj.railway.framework.convention.exception.ClientException;
import com.lzj.railway.framework.designpattern.chain.ChainDecision;
import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.user.common.errorcode.UserErrorCode;
import com.lzj.railway.user.dto.request.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/** 注册责任链第一步：校验标准化后的注册参数。 */
@Component
@RequiredArgsConstructor
public class RegisterParameterValidationHandler implements ChainHandler<RegisterRequest> {

    private final Validator validator;

    /**
     * 执行 DTO 约束校验，使控制器之外的服务调用也不会绕过参数规则。
     *
     * @param context 注册上下文
     * @return 参数合法时继续执行责任链
     */
    @Override
    public ChainDecision handle(RegisterRequest request) {
        ConstraintViolation<?> violation = validator.validate(request).stream()
                .min(Comparator.comparing(item -> item.getPropertyPath().toString()))
                .orElse(null);
        if (violation != null) {
            String message = violation.getPropertyPath() + ": " + violation.getMessage();
            throw new ClientException(message, UserErrorCode.REGISTRATION_PARAMETER_INVALID);
        }
        return ChainDecision.CONTINUE;
    }

    /** 参数校验必须最先执行。 */
    @Override
    public int order() {
        return 0;
    }
}
