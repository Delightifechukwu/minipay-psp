package com.minipay.settlements.domain;

import com.minipay.payments.domain.Payment;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "settlement_items",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_settlement_item",
               columnNames = {"batch_id", "payment_id"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private SettlementBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal msc;

    @Column(name = "vat_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "processor_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal processorFee;

    @Column(name = "processor_vat", nullable = false, precision = 19, scale = 2)
    private BigDecimal processorVat;

    @Column(name = "amount_payable", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPayable;
}
