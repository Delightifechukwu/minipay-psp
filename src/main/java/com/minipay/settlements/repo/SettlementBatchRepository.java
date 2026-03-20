package com.minipay.settlements.repo;

import com.minipay.settlements.domain.SettlementBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long> {

    Optional<SettlementBatch> findBySettlementRef(UUID ref);

    boolean existsByMerchantIdAndPeriodStartAndPeriodEnd(
            Long merchantId, LocalDate from, LocalDate to);

    @Query("""
        SELECT b FROM SettlementBatch b
        WHERE (:merchantId IS NULL OR b.merchant.merchantId = :merchantId)
          AND (:from IS NULL OR b.periodStart >= :from)
          AND (:to   IS NULL OR b.periodEnd   <= :to)
        """)
    Page<SettlementBatch> findByFilters(
            @Param("merchantId") String merchantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    @Query("SELECT b FROM SettlementBatch b LEFT JOIN FETCH b.items WHERE b.settlementRef = :ref")
    Optional<SettlementBatch> findBySettlementRefWithItems(@Param("ref") UUID ref);
}
