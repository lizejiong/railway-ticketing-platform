package com.lzj.railway.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 本地模拟支付开关。
 *
 * <p>默认关闭，避免部署环境误开放可绕过第三方支付的确认入口。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "pay.mock")
public class MockPaymentProperties {
    private boolean enabled;
}
