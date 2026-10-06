package com.lzj.railway.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 生成可按订单号单独路由的订单号。
 *
 * <p>末六位固定为用户标识末六位，因此复合分片算法在缺失 user_id 时，
 * 仍能将 order_sn 查询路由到用户所在的同一库表。</p>
 */
@Component
@RequiredArgsConstructor
public class OrderNumberGenerator {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final StringRedisTemplate redisTemplate;

    /** 为指定用户生成全局唯一订单号。 */
    public String generate(Long userId) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        Long sequence = redisTemplate.opsForValue().increment("railway:order:sequence:" + timestamp);
        long actualSequence = sequence == null ? 0L : Math.floorMod(sequence, 100_000L);
        String userIdText = String.valueOf(userId);
        String routingTail = userIdText.substring(Math.max(0, userIdText.length() - 6));
        return timestamp + String.format("%05d", actualSequence) + String.format("%6s", routingTail).replace(' ', '0');
    }
}
