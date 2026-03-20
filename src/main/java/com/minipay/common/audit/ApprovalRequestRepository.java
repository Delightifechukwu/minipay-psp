package com.minipay.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    Optional<ApprovalRequest> findByRequestRef(UUID ref);
    Page<ApprovalRequest> findByStatus(String status, Pageable pageable);
    Page<ApprovalRequest> findByMakerUsername(String maker, Pageable pageable);
}
