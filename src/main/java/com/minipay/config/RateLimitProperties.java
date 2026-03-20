package com.minipay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit.payment")
public class RateLimitProperties {
    private long capacity = 100;
    private long refillTokens = 100;
    private long refillSeconds = 60;
}
