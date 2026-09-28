package com.lzj.railway.framework.starter.user.core;

import com.alibaba.ttl.threadpool.TtlExecutors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextTest {

    @AfterEach
    void clearContext() {
        UserContext.removeUser();
    }

    @Test
    void shouldBindReadAndRemoveCurrentUser() {
        UserInfoDTO user = user();

        UserContext.setUser(user);

        assertThat(UserContext.getUser()).isSameAs(user);
        assertThat(UserContext.getUserId()).isEqualTo("10001");
        assertThat(UserContext.getUsername()).isEqualTo("traveler");
        assertThat(UserContext.getRealName()).isEqualTo("Rail User");
        assertThat(UserContext.getToken()).isEqualTo("signed-token");

        UserContext.removeUser();

        assertThat(UserContext.getUser()).isNull();
        assertThat(UserContext.getUserId()).isNull();
    }

    @Test
    void shouldTransmitSnapshotThroughTtlExecutorAndRestoreWorkerContext() throws Exception {
        ExecutorService rawExecutor = Executors.newSingleThreadExecutor();
        ExecutorService ttlExecutor = TtlExecutors.getTtlExecutorService(rawExecutor);
        try {
            UserContext.setUser(user());

            assertThat(ttlExecutor.submit(UserContext::getUserId).get(3, TimeUnit.SECONDS))
                    .isEqualTo("10001");

            UserContext.removeUser();

            assertThat(ttlExecutor.submit(UserContext::getUser).get(3, TimeUnit.SECONDS))
                    .isNull();
        } finally {
            ttlExecutor.shutdownNow();
        }
    }

    private UserInfoDTO user() {
        return UserInfoDTO.builder()
                .userId("10001")
                .username("traveler")
                .realName("Rail User")
                .token("signed-token")
                .build();
    }
}
