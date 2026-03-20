package com.minipay.payments;

import com.minipay.common.errors.RateLimitExceededException;
import com.minipay.config.RateLimitProperties;
import com.minipay.payments.service.PaymentRateLimiter;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PaymentRateLimiter Unit Tests")
class PaymentRateLimiterTest {

    private PaymentRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties();
        props.setCapacity(2);
        props.setRefillTokens(2);
        props.setRefillSeconds(60);
        rateLimiter = new PaymentRateLimiter(props);
    }

    @Test
    @DisplayName("First request within limit is allowed")
    void checkLimit_withinCapacity_noException() {
        assertThatNoException().isThrownBy(() -> rateLimiter.checkLimit("merchant-A"));
    }

    @Test
    @DisplayName("Requests beyond capacity throw RateLimitExceededException")
    void checkLimit_exceedsCapacity_throws() {
        rateLimiter.checkLimit("merchant-B");
        rateLimiter.checkLimit("merchant-B"); // capacity = 2, so this is still fine
        assertThatThrownBy(() -> rateLimiter.checkLimit("merchant-B"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("merchant-B");
    }

    @Test
    @DisplayName("Different merchants have separate buckets")
    void checkLimit_differentMerchants_separateBuckets() {
        // Exhaust merchant-C bucket
        rateLimiter.checkLimit("merchant-C");
        rateLimiter.checkLimit("merchant-C");
        assertThatThrownBy(() -> rateLimiter.checkLimit("merchant-C"))
                .isInstanceOf(RateLimitExceededException.class);

        // merchant-D should still be unaffected
        assertThatNoException().isThrownBy(() -> rateLimiter.checkLimit("merchant-D"));
    }

    @Test
    @DisplayName("Rate limit exception carries retry-after seconds")
    void checkLimit_exceeded_hasRetryAfter() {
        rateLimiter.checkLimit("merchant-E");
        rateLimiter.checkLimit("merchant-E");

        assertThatThrownBy(() -> rateLimiter.checkLimit("merchant-E"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isGreaterThan(0);
                });
    }
}