package com.minipay.webhooks.service;

import com.minipay.common.utils.WebhookSignatureUtil;
import com.minipay.webhooks.domain.WebhookEvent;
import com.minipay.webhooks.repo.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls for due webhook events and delivers them with exponential backoff.
 *
 * <p>Retry schedule (initial 1s, multiplier 2.0, max 30s):
 * <ul>
 *   <li>Attempt 1: immediate</li>
 *   <li>Attempt 2: +1s</li>
 *   <li>Attempt 3: +2s</li>
 *   <li>Attempt 4: +4s</li>
 *   <li>Attempt 5: +8s → DLQ if fails</li>
 * </ul>
 * Events exceeding maxAttempts are promoted to DLQ status for manual review.
 * HTTP delivery is protected by a Resilience4j circuit breaker (see WebhookHttpDelivery).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookRetryScheduler {

    private static final long INITIAL_DELAY_MS    = 1_000L;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_DELAY_MS        = 30_000L;

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookHttpDelivery webhookHttpDelivery;

    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(name = "webhookRetryScheduler", lockAtMostFor = "PT4S", lockAtLeastFor = "PT1S")
    @Transactional
    public void processQueue() {
        List<WebhookEvent> dueEvents = webhookEventRepository.findDueForRetry(Instant.now());
        if (!dueEvents.isEmpty()) {
            log.debug("Processing {} due webhook events", dueEvents.size());
        }
        dueEvents.forEach(this::deliver);
    }

    private void deliver(WebhookEvent event) {
        event.setAttemptCount(event.getAttemptCount() + 1);
        String signature = WebhookSignatureUtil.sign(
                event.getPayload(), event.getMerchant().getWebhookSecret());

        try {
            int statusCode = webhookHttpDelivery.send(
                    event.getTargetUrl(),
                    event.getPayload(),
                    signature,
                    event.getEventType(),
                    event.getAttemptCount());

            if (statusCode >= 200 && statusCode < 300) {
                event.setStatus("SUCCESS");
                event.setLastError(null);
                log.info("Webhook delivered successfully: event={} attempt={} status={}",
                        event.getId(), event.getAttemptCount(), statusCode);
            } else {
                handleFailure(event, "HTTP " + statusCode);
            }
        } catch (Exception e) {
            handleFailure(event, e.getClass().getSimpleName() + ": " + truncate(e.getMessage(), 200));
        }

        webhookEventRepository.save(event);
    }

    private void handleFailure(WebhookEvent event, String error) {
        event.setLastError(error);

        if (event.getAttemptCount() >= event.getMaxAttempts()) {
            event.setStatus("DLQ");
            log.error("Webhook moved to DLQ after {} attempts: event={} url={} error={}",
                    event.getAttemptCount(), event.getId(), event.getTargetUrl(), error);
        } else {
            event.setStatus("PENDING");
            long delayMs = (long) (INITIAL_DELAY_MS *
                    Math.pow(BACKOFF_MULTIPLIER, event.getAttemptCount() - 1));
            delayMs = Math.min(delayMs, MAX_DELAY_MS);
            event.setNextRetryAt(Instant.now().plusMillis(delayMs));
            log.warn("Webhook delivery failed (attempt {}/{}): event={} retryIn={}ms error={}",
                    event.getAttemptCount(), event.getMaxAttempts(),
                    event.getId(), delayMs, error);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}