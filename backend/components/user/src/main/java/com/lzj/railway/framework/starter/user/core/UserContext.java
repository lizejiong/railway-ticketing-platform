package com.lzj.railway.framework.starter.user.core;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Holds the authenticated user associated with the current execution context.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserContext {

    private static final TransmittableThreadLocal<UserInfoDTO> USER_HOLDER =
            new TransmittableThreadLocal<>();

    public static void setUser(UserInfoDTO user) {
        USER_HOLDER.set(Objects.requireNonNull(user, "user must not be null"));
    }

    public static UserInfoDTO getUser() {
        return USER_HOLDER.get();
    }

    public static String getUserId() {
        UserInfoDTO user = getUser();
        return user == null ? null : user.getUserId();
    }

    public static String getUsername() {
        UserInfoDTO user = getUser();
        return user == null ? null : user.getUsername();
    }

    public static String getRealName() {
        UserInfoDTO user = getUser();
        return user == null ? null : user.getRealName();
    }

    public static String getToken() {
        UserInfoDTO user = getUser();
        return user == null ? null : user.getToken();
    }

    public static void removeUser() {
        USER_HOLDER.remove();
    }
}
