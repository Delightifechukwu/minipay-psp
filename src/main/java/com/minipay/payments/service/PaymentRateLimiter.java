package com.minipay.payments.service;

import com.minipay.common.errors.RateLimitExceededException;
import com.minipay.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Per-merchant fixed-window rate limiter backed by Redis.
 * Uses an atomic Lua INCR+EXPIRE script for correctness across multiple app instances.
 * Fails open (allows the request) if Redis is unavailable to preserve availability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRateLimiter {

    private final RateLimitProperties props;
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "rate:payment:";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
            "local c = redis.call('INCR', KEYS[1]) " +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return c"
        );
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    public void checkLimit(String merchantId) {
        String key = KEY_PREFIX + merchantId;
        try {
            Long count = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(key),
                    String.valueOf(props.getRefillSeconds())
            );
            if (count != null && count > props.getCapacity()) {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                long retryAfter = (ttl != null && ttl > 0) ? ttl : props.getRefillSeconds();
                throw new RateLimitExceededException(
                        "Payment rate limit exceeded for merchant: " + merchantId, retryAfter);
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis rate limiter unavailable for merchant: {}, allowing request through", merchantId, e);
        }
    }
}