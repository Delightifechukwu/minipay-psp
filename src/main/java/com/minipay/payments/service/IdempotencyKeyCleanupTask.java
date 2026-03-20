package com.minipay.payments.service;

import com.minipay.payments.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Clears idempotency keys older than 24 hours so they cannot cause duplicate-detection
 * false positives on new payments with recycled order IDs, and prevents unbounded DB growth.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyKeyCleanupTask {

    private final PaymentRepository paymentRepository;

    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    @SchedulerLock(name = "idempotencyKeyCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void clearExpiredKeys() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        int cleared = paymentRepository.clearIdempotencyKeysBefore(cutoff);
        log.info("Cleared {} expired idempotency keys (older than 24h)", cleared);
    }
}