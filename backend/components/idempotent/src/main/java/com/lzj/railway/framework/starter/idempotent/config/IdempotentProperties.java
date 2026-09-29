package com.lzj.railway.framework.starter.idempotent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 幂等组件配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "railway.idempotent")
public class IdempotentProperties {

    /**
     * 是否启用幂等组件。
     */
    private boolean enabled = true;

    /**
     * Redis 幂等 Key 的统一前缀。
     */
    private String keyPrefix = "railway:idempotent";

    /**
     * TOKEN 模式读取的 HTTP 请求头名称。
     */
    private String tokenHeader = "Idempotency-Key";
}
