package com.lzj.railway.framework.starter.user.filter;

import com.lzj.railway.framework.starter.base.constant.FilterOrderConstant;
import com.lzj.railway.framework.starter.user.config.UserProperties;
import com.lzj.railway.framework.starter.user.core.UserContext;
import com.lzj.railway.framework.starter.user.core.UserInfoDTO;
import com.lzj.railway.framework.starter.user.token.JwtTokenGenerator;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextFilterTest {

    private static final String SECRET = "railway-user-component-secret-32-bytes";

    @AfterEach
    void clearContext() {
        UserContext.removeUser();
    }

    @Test
    void shouldContinueWithoutUserWhenHeaderIsMissing() throws Exception {
        UserContextFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            invoked.set(true);
            assertThat(UserContext.getUser()).isNull();
        });

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldBindVerifiedUserDuringRequestAndClearAfterwards() throws Exception {
        UserProperties.Jwt properties = properties();
        JwtTokenGenerator generator = new JwtTokenGenerator(properties);
        UserContextFilter filter = new UserContextFilter(generator, properties.getHeaderName());
        String token = generator.generateToken(user());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(properties.getHeaderName(), "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<UserInfoDTO> captured = new AtomicReference<>();

        filter.doFilter(request, response,
                (servletRequest, servletResponse) -> captured.set(UserContext.getUser()));

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getUserId()).isEqualTo("10001");
        assertThat(captured.get().getToken()).isEqualTo(token);
        assertThat(UserContext.getUser()).isNull();
    }

    @Test
    void shouldReturnUnauthorizedAndSkipChainForInvalidToken() throws Exception {
        UserContextFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response,
                (servletRequest, servletResponse) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(UserContext.getUser()).isNull();
    }

    @Test
    void shouldClearUserWhenDownstreamChainFails() {
        UserProperties.Jwt properties = properties();
        JwtTokenGenerator generator = new JwtTokenGenerator(properties);
        UserContextFilter filter = new UserContextFilter(generator, properties.getHeaderName());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(properties.getHeaderName(), generator.generateToken(user()));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(UserContext.getUserId()).isEqualTo("10001");
            throw new ServletException("downstream failure");
        })).isInstanceOf(ServletException.class);

        assertThat(UserContext.getUser()).isNull();
    }

    @Test
    void shouldUseSharedFilterOrder() {
        assertThat(filter().getOrder()).isEqualTo(FilterOrderConstant.USER_TRANSMIT_FILTER_ORDER);
    }

    private UserContextFilter filter() {
        UserProperties.Jwt properties = properties();
        return new UserContextFilter(new JwtTokenGenerator(properties), properties.getHeaderName());
    }

    private UserProperties.Jwt properties() {
        UserProperties.Jwt properties = new UserProperties.Jwt();
        properties.setSecret(SECRET);
        properties.setIssuer("railway-test");
        return properties;
    }

    private UserInfoDTO user() {
        return UserInfoDTO.builder()
                .userId("10001")
                .username("traveler")
                .realName("Rail User")
                .build();
    }
}
