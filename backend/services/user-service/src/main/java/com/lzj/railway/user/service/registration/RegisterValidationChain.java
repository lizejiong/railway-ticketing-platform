package com.lzj.railway.user.service.registration;

import com.lzj.railway.framework.designpattern.chain.ChainHandler;
import com.lzj.railway.framework.designpattern.chain.ResponsibilityChain;
import com.lzj.railway.user.dto.request.RegisterRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 注册校验责任链的业务入口。
 *
 * <p>负责标准化请求并依次执行注册处理器，调用方无需感知通用责任链的装配细节。</p>
 */
@Component
public class RegisterValidationChain {

    private final ResponsibilityChain<RegisterRequest> responsibilityChain;

    /** 收集注册处理器，并按照处理器声明的顺序构建不可变责任链。 */
    public RegisterValidationChain(List<ChainHandler<RegisterRequest>> handlers) {
        this.responsibilityChain = ResponsibilityChain.<RegisterRequest>builder()
                .addAll(handlers)
                .build();
    }

    /**
     * 创建标准化请求、执行责任链，并返回后续加锁和事务可以直接使用的请求。
     *
     * @param source 原始注册请求
     * @return 标准化且校验通过的注册请求
     */
    public RegisterRequest validateAndNormalize(RegisterRequest source) {
        Objects.requireNonNull(source, "注册请求不能为空");
        RegisterRequest normalized = new RegisterRequest();
        normalized.setUsername(trim(source.getUsername()));
        normalized.setPassword(source.getPassword());
        normalized.setPhone(trim(source.getPhone()));
        normalized.setEmail(lowerCase(source.getEmail()));
        normalized.setRealName(trim(source.getRealName()));
        normalized.setIdType(source.getIdType());
        normalized.setIdCard(upperCase(source.getIdCard()));
        responsibilityChain.execute(normalized);
        return normalized;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String lowerCase(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String upperCase(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }
}
