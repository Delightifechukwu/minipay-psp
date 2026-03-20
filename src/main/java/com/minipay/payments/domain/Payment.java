package com.minipay.payments.domain;

import com.minipay.merchants.domain.Merchant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_ref", nullable = false, unique = true,
            columnDefinition = "UUID DEFAULT gen_random_uuid()")
    @Builder.Default
    private UUID paymentRef = UUID.randomUUID();

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "NGN";

    @Column(nullable = false, length = 20)
    private String channel; // CARD|WALLET|BANK_TRANSFER

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

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

    @Column(name = "payable_vat", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal payableVat = BigDecimal.ZERO;

    @Column(name = "amount_payable", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal amountPayable = BigDecimal.ZERO;

    @Column(name = "customer_id", length = 100)
    private String customerId;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
        if (paymentRef == null) paymentRef = UUID.randomUUID();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
