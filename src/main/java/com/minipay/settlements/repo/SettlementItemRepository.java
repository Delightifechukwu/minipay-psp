package com.minipay.settlements.repo;

import com.minipay.settlements.domain.SettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

    @Query("SELECT i FROM SettlementItem i JOIN FETCH i.payment WHERE i.batch.id = :batchId")
    List<SettlementItem> findByBatchId(@Param("batchId") Long batchId);
}
