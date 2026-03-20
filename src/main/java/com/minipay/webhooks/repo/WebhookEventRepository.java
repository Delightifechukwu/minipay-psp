package com.minipay.webhooks.repo;

import com.minipay.webhooks.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    @Query("""
        SELECT w FROM WebhookEvent w
        JOIN FETCH w.merchant
        WHERE w.status = 'PENDING'
          AND (w.nextRetryAt IS NULL OR w.nextRetryAt <= :now)
        ORDER BY w.createdAt ASC
        LIMIT 50
        """)
    List<WebhookEvent> findDueForRetry(Instant now);

    long countByStatus(String status);
}
