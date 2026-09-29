package com.lzj.railway.framework.starter.idempotent.key;

import com.lzj.railway.framework.convention.exception.ClientException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 从当前 Servlet 请求头读取幂等 Token。
 */
public class HttpHeaderIdempotentTokenProvider implements IdempotentTokenProvider {

    private final String headerName;

    public HttpHeaderIdempotentTokenProvider(String headerName) {
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalArgumentException("幂等 Token 请求头名称不能为空");
        }
        this.headerName = headerName;
    }

    @Override
    public String getToken() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            throw new IllegalStateException("TOKEN 幂等模式只能在 Servlet HTTP 请求中使用");
        }
        String token = servletAttributes.getRequest().getHeader(headerName);
        if (token == null || token.isBlank()) {
            throw new ClientException("请求头 " + headerName + " 不能为空");
        }
        return token.trim();
    }
}
