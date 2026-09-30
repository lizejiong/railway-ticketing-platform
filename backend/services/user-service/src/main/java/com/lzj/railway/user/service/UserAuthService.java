package com.lzj.railway.user.service;

import com.lzj.railway.user.dto.request.LoginRequest;
import com.lzj.railway.user.dto.request.RefreshTokenRequest;
import com.lzj.railway.user.dto.request.RegisterRequest;
import com.lzj.railway.user.dto.response.LoginResponse;
import com.lzj.railway.user.dto.response.RegisterResponse;

public interface UserAuthService {

    /**
     * 注册新账号，并同步创建手机号和邮箱索引。
     *
     * @param request 已通过参数校验的注册信息
     * @return 新创建的用户基础信息
     */
    RegisterResponse register(RegisterRequest request);

    /**
     * 使用用户名、手机号或邮箱完成账号认证。
     *
     * @param request 账号标识和明文密码
     * @return Access Token、Refresh Token 与用户基础信息
     */
    LoginResponse login(LoginRequest request);

    /**
     * 消费旧 Refresh Token，并轮换签发一组新 Token。
     *
     * @param request 当前有效的 Refresh Token
     * @return 新的 Access Token、Refresh Token 与用户基础信息
     */
    LoginResponse refresh(RefreshTokenRequest request);

    /**
     * 撤销当前 Refresh Token 会话。
     *
     * @param request 需要撤销的 Refresh Token
     */
    void logout(RefreshTokenRequest request);
}
