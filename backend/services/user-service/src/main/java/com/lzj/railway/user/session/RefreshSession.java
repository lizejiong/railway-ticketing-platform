package com.lzj.railway.user.session;

import java.time.Instant;

/** 不透明 Refresh Token 在服务端保存的会话状态。 */
public record RefreshSession(Long userId, String username, Instant createdAt) {
}
