package com.lzj.railway.pay.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 生成支付流水号。
 *
 * <p>尾部复用订单号的六位路由片段，使支付单可以按订单号或支付流水号精确分片。</p>
 */
@Component
@RequiredArgsConstructor
public class PayIdGenerator {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private final StringRedisTemplate redisTemplate;

    /** 为一笔订单生成全局唯一的支付流水号。 */
    public String generate(String orderSn) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        Long sequence = redisTemplate.opsForValue().increment("railway:pay:sequence:" + timestamp);
        long actualSequence = sequence == null ? 0L : Math.floorMod(sequence, 100_000L);
        String tail = orderSn.substring(Math.max(0, orderSn.length() - 6));
        return timestamp + String.format("%05d", actualSequence) + String.format("%6s", tail).replace(' ', '0');
    }
}
