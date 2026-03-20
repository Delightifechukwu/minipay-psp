package com.minipay.merchants.repo;

import com.minipay.merchants.domain.Merchant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByMerchantId(String merchantId);

    boolean existsByMerchantId(String merchantId);
    boolean existsByEmail(String email);

    @Query("""
        SELECT m FROM Merchant m
        WHERE (:status IS NULL OR m.status = :status)
          AND (:name IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', cast(:name as String), '%')))
        """)
    Page<Merchant> findByFilters(@Param("status") String status, @Param("name") String name, Pageable pageable);

    @Query("SELECT m FROM Merchant m LEFT JOIN FETCH m.chargeSetting WHERE m.merchantId = :merchantId")
    Optional<Merchant> findByMerchantIdWithChargeSettings(String merchantId);
}
