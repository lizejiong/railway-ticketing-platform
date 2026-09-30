package com.lzj.railway.user.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * 定义用户服务自动生成的 OpenAPI 文档基础信息。
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "12306 铁路平台 - 用户服务",
        version = "0.1.0",
        description = "用户注册、登录与后续用户域接口的 OpenAPI 定义。"))
public class OpenApiConfiguration {
}
