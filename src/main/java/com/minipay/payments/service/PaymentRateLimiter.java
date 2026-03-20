package com.minipay.payments.service;

import com.minipay.common.errors.RateLimitExceededException;
import com.minipay.config.RateLimitProperties;
import io.github.bucket4j.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-merchant token-bucket rate limiter for payment initiation.
 * Uses in-memory Bucket4j; swap backing store to Redis for multi-instance deployments.
 */
@Service
@RequiredArgsConstructor
public class PaymentRateLimiter {

    private final RateLimitProperties props;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void checkLimit(String merchantId) {
        Bucket bucket = buckets.computeIfAbsent(merchantId, this::newBucket);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfter = probe.getNanosToWaitForRefill() / 1_000_000_000L;
            throw new RateLimitExceededException(
                    "Payment rate limit exceeded for merchant: " + merchantId, retryAfter);
        }
    }

    private Bucket newBucket(String merchantId) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(props.getCapacity())
                        .refillIntervally(props.getRefillTokens(),
                                Duration.ofSeconds(props.getRefillSeconds()))
                        .build())
                .build();
    }
}
