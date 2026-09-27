package com.lzj.railway.framework.starter.base.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Keys used to carry authenticated user information between services.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserConstant {

    public static final String USER_ID_KEY = "userId";
    public static final String USER_NAME_KEY = "username";
    public static final String REAL_NAME_KEY = "realName";
    public static final String USER_TOKEN_KEY = "token";

}
