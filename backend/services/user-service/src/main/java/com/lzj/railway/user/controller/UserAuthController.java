package com.lzj.railway.user.controller;

import com.lzj.railway.framework.convention.result.Result;
import com.lzj.railway.framework.starter.idempotent.annotation.Idempotent;
import com.lzj.railway.framework.starter.web.result.Results;
import com.lzj.railway.user.dto.request.LoginRequest;
import com.lzj.railway.user.dto.request.RefreshTokenRequest;
import com.lzj.railway.user.dto.request.RegisterRequest;
import com.lzj.railway.user.dto.response.LoginResponse;
import com.lzj.railway.user.dto.response.RegisterResponse;
import com.lzj.railway.user.service.UserAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户认证", description = "账号注册与登录")
public class UserAuthController {

    private final UserAuthService userAuthService;

    @PostMapping("/register")
    @Idempotent(uniqueKeyPrefix = "user:register")
    @Operation(summary = "账号注册", description = "校验用户名、手机号和邮箱唯一性后创建用户账号。")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Results.success(userAuthService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "账号登录", description = "支持用户名、手机号或邮箱加密码登录，并签发 Access Token 与 Refresh Token。")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Results.success(userAuthService.login(request));
    }

    /** 使用一次性 Refresh Token 轮换一组新 Token。 */
    @PostMapping("/token/refresh")
    @Operation(summary = "刷新登录令牌", description = "消费旧 Refresh Token，并签发新的 Access Token 与 Refresh Token。")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Results.success(userAuthService.refresh(request));
    }

    /** 撤销 Refresh Token 会话，结束可续期的登录状态。 */
    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "撤销 Refresh Token；已签发的 Access Token 按短有效期自然失效。")
    public Result<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        userAuthService.logout(request);
        return Results.success();
    }
}
