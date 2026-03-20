package com.minipay.webhooks.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin HTTP wrapper for webhook delivery with a Resilience4j circuit breaker.
 * When the circuit opens (50% failure rate over 10 calls), delivery attempts
 * are short-circuited for 30 seconds before entering half-open state.
 */
@Service
@Slf4j
public class WebhookHttpDelivery {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @CircuitBreaker(name = "webhookDelivery", fallbackMethod = "fallback")
    public int send(String url, String payload, String signature,
                    String eventType, int attempt) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Signature", signature)
                .header("X-Event-Type", eventType)
                .header("X-Attempt", String.valueOf(attempt))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    public int fallback(String url, String payload, String signature,
                        String eventType, int attempt, Exception ex) {
        log.warn("Circuit breaker open for webhook delivery to {}: {}", url, ex.getMessage());
        throw new RuntimeException("Circuit open: " + ex.getMessage(), ex);
    }
}