package com.lzj.railway.framework.starter.user.filter;

import com.lzj.railway.framework.starter.base.constant.FilterOrderConstant;
import com.lzj.railway.framework.starter.user.core.UserContext;
import com.lzj.railway.framework.starter.user.core.UserInfoDTO;
import com.lzj.railway.framework.starter.user.token.JwtTokenGenerator;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Resolves a verified user token and binds its claims for the duration of one request.
 */
public class UserContextFilter extends OncePerRequestFilter implements Ordered {

    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired user token";

    private final JwtTokenGenerator tokenGenerator;
    private final String headerName;

    public UserContextFilter(JwtTokenGenerator tokenGenerator, String headerName) {
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator, "tokenGenerator must not be null");
        if (!StringUtils.hasText(headerName)) {
            throw new IllegalArgumentException("headerName must not be blank");
        }
        this.headerName = headerName;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        UserContext.removeUser();
        try {
            String tokenValue = request.getHeader(headerName);
            if (!StringUtils.hasText(tokenValue)) {
                filterChain.doFilter(request, response);
                return;
            }

            UserInfoDTO user;
            try {
                user = tokenGenerator.parseToken(tokenValue);
            } catch (JwtException | IllegalArgumentException exception) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, INVALID_TOKEN_MESSAGE);
                return;
            }

            UserContext.setUser(user);
            filterChain.doFilter(request, response);
        } finally {
            UserContext.removeUser();
        }
    }

    @Override
    public int getOrder() {
        return FilterOrderConstant.USER_TRANSMIT_FILTER_ORDER;
    }
}
