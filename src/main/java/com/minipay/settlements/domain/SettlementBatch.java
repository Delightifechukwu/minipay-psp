package com.minipay.settlements.domain;

import com.minipay.merchants.domain.Merchant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "settlement_batches",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_settlement_merchant_period",
               columnNames = {"merchant_id", "period_start", "period_end"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_ref", nullable = false, unique = true,
            columnDefinition = "UUID DEFAULT gen_random_uuid()")
    @Builder.Default
    private UUID settlementRef = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    @Builder.Default
    private int count = 0;

    @Column(name = "transaction_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal transactionAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal msc = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(name = "processor_fee", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal processorFee = BigDecimal.ZERO;

    @Column(name = "processor_vat", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal processorVat = BigDecimal.ZERO;

    /** MiniPay income = msc - processorFee */
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal income = BigDecimal.ZERO;

    @Column(name = "payable_vat", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal payableVat = BigDecimal.ZERO;

    @Column(name = "amount_payable", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal amountPayable = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING|POSTED

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SettlementItem> items = new ArrayList<>();
}
