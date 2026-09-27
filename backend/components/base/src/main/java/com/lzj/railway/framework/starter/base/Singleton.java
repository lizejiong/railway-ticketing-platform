package com.lzj.railway.framework.starter.base;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Process-local singleton object container.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Singleton {

    private static final ConcurrentHashMap<String, Object> SINGLE_OBJECT_POOL = new ConcurrentHashMap<>();

    /**
     * Returns the object registered under {@code key}, or {@code null} when absent.
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return (T) SINGLE_OBJECT_POOL.get(key);
    }

    /**
     * Returns the registered object, creating it atomically when absent.
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<? extends T> supplier) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        return (T) SINGLE_OBJECT_POOL.computeIfAbsent(
                key,
                ignored -> Objects.requireNonNull(supplier.get(), "supplier must not return null")
        );
    }

    /**
     * Registers an object using its fully qualified class name as the key.
     */
    public static void put(Object value) {
        Objects.requireNonNull(value, "value must not be null");
        put(value.getClass().getName(), value);
    }

    /**
     * Registers or replaces an object under {@code key}.
     */
    public static void put(String key, Object value) {
        SINGLE_OBJECT_POOL.put(
                Objects.requireNonNull(key, "key must not be null"),
                Objects.requireNonNull(value, "value must not be null")
        );
    }
}
