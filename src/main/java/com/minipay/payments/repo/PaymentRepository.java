package com.minipay.payments.repo;

import com.minipay.payments.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentRef(UUID paymentRef);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentRef = :ref")
    Optional<Payment> findByPaymentRefForUpdate(@Param("ref") UUID ref);

    @Query("""
        SELECT p FROM Payment p
        WHERE (cast(:merchantId as String) IS NULL OR p.merchant.merchantId = cast(:merchantId as String))
          AND (cast(:channel as String)   IS NULL OR p.channel = cast(:channel as String))
          AND (cast(:status as String)    IS NULL OR p.status  = cast(:status as String))
          AND p.createdAt >= :from
          AND p.createdAt <= :to
        """)
    Page<Payment> findByFilters(
            @Param("merchantId") String merchantId,
            @Param("channel")    String channel,
            @Param("status")     String status,
            @Param("from")       Instant from,
            @Param("to")         Instant to,
            Pageable pageable);

    @Query("""
        SELECT p FROM Payment p
        WHERE p.merchant.merchantId = :merchantId
          AND p.status = 'SUCCESS'
          AND p.createdAt >= :from
          AND p.createdAt <= :to
        """)
    List<Payment> findSuccessfulForSettlement(
            @Param("merchantId") String merchantId,
            @Param("from")       Instant from,
            @Param("to")         Instant to);

    @Modifying
    @Query("UPDATE Payment p SET p.idempotencyKey = NULL WHERE p.idempotencyKey IS NOT NULL AND p.createdAt < :cutoff")
    int clearIdempotencyKeysBefore(@Param("cutoff") Instant cutoff);

    // Used for reporting export — no pagination
    @Query("""
        SELECT p FROM Payment p
        WHERE (cast(:merchantId as String) IS NULL OR p.merchant.merchantId = cast(:merchantId as String))
          AND (cast(:channel as String)   IS NULL OR p.channel = cast(:channel as String))
          AND (cast(:status as String)    IS NULL OR p.status  = cast(:status as String))
          AND p.createdAt >= :from
          AND p.createdAt <= :to
        ORDER BY p.createdAt DESC
        """)
    List<Payment> findForExport(
            @Param("merchantId") String merchantId,
            @Param("channel")    String channel,
            @Param("status")     String status,
            @Param("from")       Instant from,
            @Param("to")         Instant to);
}
