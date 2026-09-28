package com.lzj.railway.framework.starter.user.core;

import lombok.Builder;
import lombok.Value;

import java.io.Serial;
import java.io.Serializable;

/**
 * Immutable identity data carried by a verified user token.
 */
@Value
@Builder
public class UserInfoDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    String userId;
    String username;
    String realName;
    String token;
}
