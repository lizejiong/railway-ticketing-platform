package com.lzj.railway.framework.starter.idempotent.key;

import com.lzj.railway.framework.convention.exception.ClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpHeaderIdempotentTokenProviderTest {

    private final HttpHeaderIdempotentTokenProvider provider =
            new HttpHeaderIdempotentTokenProvider("Idempotency-Key");

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldReadTokenFromCurrentRequestHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", " TOKEN-100 ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(provider.getToken()).isEqualTo("TOKEN-100");
    }

    @Test
    void shouldRejectRequestWithoutTokenHeader() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThatThrownBy(provider::getToken)
                .isInstanceOf(ClientException.class)
                .hasMessage("请求头 Idempotency-Key 不能为空");
    }
}
