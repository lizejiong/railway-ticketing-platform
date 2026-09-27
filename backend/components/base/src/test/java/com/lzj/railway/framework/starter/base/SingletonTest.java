package com.lzj.railway.framework.starter.base;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SingletonTest {

    @Test
    void shouldStoreObjectByExplicitKey() {
        String key = UUID.randomUUID().toString();
        Object expected = new Object();

        Singleton.put(key, expected);
        Object actual = Singleton.get(key);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void shouldStoreObjectByClassName() {
        SampleSingleton expected = new SampleSingleton();

        Singleton.put(expected);

        assertThat(Singleton.<SampleSingleton>get(SampleSingleton.class.getName())).isSameAs(expected);
    }

    @Test
    void shouldCreateObjectOnlyOnceUnderConcurrentAccess() throws Exception {
        String key = UUID.randomUUID().toString();
        AtomicInteger creationCount = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return Singleton.get(key, () -> {
                        creationCount.incrementAndGet();
                        return new Object();
                    });
                }));
            }

            start.countDown();
            Object expected = futures.get(0).get(5, TimeUnit.SECONDS);
            for (Future<Object> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isSameAs(expected);
            }
            assertThat(creationCount).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class SampleSingleton {
    }
}
