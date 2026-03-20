package com.minipay.merchants.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "charge_settings")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargeSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false, unique = true)
    private Merchant merchant;

    @Column(name = "percentage_fee", nullable = false, precision = 10, scale = 6)
    @Builder.Default
    private BigDecimal percentageFee = BigDecimal.ZERO;

    @Column(name = "fixed_fee", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal fixedFee = BigDecimal.ZERO;

    @Column(name = "use_fixed_msc", nullable = false)
    @Builder.Default
    private Boolean useFixedMSC = false;

    @Column(name = "msc_cap", precision = 19, scale = 2)
    private BigDecimal mscCap;

    @Column(name = "vat_rate", nullable = false, precision = 10, scale = 6)
    @Builder.Default
    private BigDecimal vatRate = BigDecimal.ZERO;

    @Column(name = "platform_provider_rate", nullable = false, precision = 10, scale = 6)
    @Builder.Default
    private BigDecimal platformProviderRate = BigDecimal.ZERO;

    @Column(name = "platform_provider_cap", precision = 19, scale = 2)
    private BigDecimal platformProviderCap;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = Instant.now(); }
}
