package com.lzj.railway.ticket.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * 定义票务服务自动生成的 OpenAPI 文档基础信息。
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "12306 铁路平台 - 票务服务",
        version = "0.1.0",
        description = "车次、区间余票与后续票务域接口的 OpenAPI 定义。"))
public class OpenApiConfiguration {
}
