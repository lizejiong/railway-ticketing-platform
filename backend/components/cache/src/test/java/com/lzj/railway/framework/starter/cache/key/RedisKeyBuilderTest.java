package com.lzj.railway.framework.starter.cache.key;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisKeyBuilderTest {

    private final RedisKeyBuilder keyBuilder = new RedisKeyBuilder("railway");

    @Test
    void shouldBuildNamespacedKey() {
        assertThat(keyBuilder.build("ticket", 2026, "G123"))
                .isEqualTo("railway:ticket:2026:G123");
    }

    @Test
    void shouldBuildKeyWithClusterHashTag() {
        assertThat(keyBuilder.buildWithHashTag("seat", "G123", "carriage", 8))
                .isEqualTo("railway:seat:{G123}:carriage:8");
    }

    @Test
    void shouldRejectBlankSegments() {
        assertThatThrownBy(() -> new RedisKeyBuilder(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keyBuilder.build(" ", "1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keyBuilder.build("ticket", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keyBuilder.buildWithHashTag("ticket", " ", "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMalformedHashTag() {
        assertThatThrownBy(() -> keyBuilder.buildWithHashTag("ticket", "{G123}", "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
