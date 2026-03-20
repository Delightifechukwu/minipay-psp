package com.minipay.payments;

import com.minipay.common.errors.RateLimitExceededException;
import com.minipay.config.RateLimitProperties;
import com.minipay.payments.service.PaymentRateLimiter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRateLimiter Unit Tests")
class PaymentRateLimiterTest {

    @Mock StringRedisTemplate redisTemplate;

    private PaymentRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties();
        props.setCapacity(2);
        props.setRefillTokens(2);
        props.setRefillSeconds(60);
        rateLimiter = new PaymentRateLimiter(props, redisTemplate);
    }

    @Test
    @DisplayName("First request within limit is allowed")
    void checkLimit_withinCapacity_noException() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L);

        assertThatNoException().isThrownBy(() -> rateLimiter.checkLimit("merchant-A"));
    }

    @Test
    @DisplayName("Requests beyond capacity throw RateLimitExceededException")
    void checkLimit_exceedsCapacity_throws() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(3L); // above capacity of 2
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(30L);

        assertThatThrownBy(() -> rateLimiter.checkLimit("merchant-B"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("merchant-B");
    }

    @Test
    @DisplayName("Redis unavailable fails open — request is allowed through")
    void checkLimit_redisUnavailable_allowsRequest() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatNoException().isThrownBy(() -> rateLimiter.checkLimit("merchant-C"));
    }

    @Test
    @DisplayName("Rate limit exception carries retry-after seconds")
    void checkLimit_exceeded_hasRetryAfter() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(5L);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(45L);

        assertThatThrownBy(() -> rateLimiter.checkLimit("merchant-D"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isGreaterThan(0);
                });
    }
}