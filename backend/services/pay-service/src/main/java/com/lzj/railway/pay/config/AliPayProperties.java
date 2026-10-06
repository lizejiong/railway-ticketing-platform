package com.lzj.railway.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 支付宝开放平台配置；密钥仅从 Nacos 或部署环境注入，禁止写入代码库。 */
@Data
@Configuration
@ConfigurationProperties(prefix = "pay.alipay")
public class AliPayProperties {
    private String appId;
    private String privateKey;
    private String alipayPublicKey;
    private String serverUrl;
    private String notifyUrl;
    private String format = "json";
    private String charset = "UTF-8";
    private String signType = "RSA2";
}
