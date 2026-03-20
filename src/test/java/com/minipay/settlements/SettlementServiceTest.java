package com.minipay.settlements;

import com.minipay.common.PageResponse;
import com.minipay.common.errors.ResourceNotFoundException;
import com.minipay.merchants.domain.Merchant;
import com.minipay.merchants.repo.MerchantRepository;
import com.minipay.payments.domain.Payment;
import com.minipay.payments.repo.PaymentRepository;
import com.minipay.settlements.domain.SettlementBatch;
import com.minipay.settlements.domain.SettlementItem;
import com.minipay.settlements.dto.SettlementDtos.*;
import com.minipay.settlements.repo.SettlementBatchRepository;
import com.minipay.settlements.repo.SettlementItemRepository;
import com.minipay.settlements.service.SettlementService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementService Unit Tests")
class SettlementServiceTest {

    @Mock SettlementBatchRepository batchRepository;
    @Mock SettlementItemRepository itemRepository;
    @Mock MerchantRepository merchantRepository;
    @Mock PaymentRepository paymentRepository;

    @InjectMocks SettlementService settlementService;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = Merchant.builder()
                .id(1L).merchantId("MRC-001").name("TestMerchant").email("m@test.com")
                .status("ACTIVE").settlementAccount("001").settlementBank("GTB")
                .webhookSecret("secret")
                .build();
    }

    // ── generateSettlements ───────────────────────────────────────────────────

    @Test
    @DisplayName("generateSettlements creates batch when payments exist")
    void generateSettlements_withPayments_createsBatch() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to   = LocalDate.of(2024, 1, 1);

        when(merchantRepository.findByFilters(eq("ACTIVE"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(merchant)));
        when(batchRepository.existsByMerchantIdAndPeriodStartAndPeriodEnd(1L, from, to))
                .thenReturn(false);
        when(paymentRepository.findSuccessfulForSettlement(eq("MRC-001"), any(), any()))
                .thenReturn(List.of(buildPayment()));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GenerateResult result = settlementService.generateSettlements(from, to);

        assertThat(result.getBatchesCreated()).isEqualTo(1);
        assertThat(result.getBatchesSkipped()).isEqualTo(0);
        assertThat(result.getTotalTransactions()).isEqualTo(1);
        verify(batchRepository).save(any(SettlementBatch.class));
    }

    @Test
    @DisplayName("generateSettlements skips merchant when batch already exists")
    void generateSettlements_batchExists_skips() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to   = LocalDate.of(2024, 1, 1);

        when(merchantRepository.findByFilters(eq("ACTIVE"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(merchant)));
        when(batchRepository.existsByMerchantIdAndPeriodStartAndPeriodEnd(1L, from, to))
                .thenReturn(true);

        GenerateResult result = settlementService.generateSettlements(from, to);

        assertThat(result.getBatchesCreated()).isEqualTo(0);
        assertThat(result.getBatchesSkipped()).isEqualTo(1);
        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("generateSettlements skips merchant when no successful payments")
    void generateSettlements_noPayments_skips() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to   = LocalDate.of(2024, 1, 1);

        when(merchantRepository.findByFilters(eq("ACTIVE"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(merchant)));
        when(batchRepository.existsByMerchantIdAndPeriodStartAndPeriodEnd(1L, from, to))
                .thenReturn(false);
        when(paymentRepository.findSuccessfulForSettlement(eq("MRC-001"), any(), any()))
                .thenReturn(Collections.emptyList());

        GenerateResult result = settlementService.generateSettlements(from, to);

        assertThat(result.getBatchesCreated()).isEqualTo(0);
        assertThat(result.getBatchesSkipped()).isEqualTo(1);
        verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("generateSettlements with no active merchants returns zero counts")
    void generateSettlements_noMerchants_returnsZero() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to   = LocalDate.of(2024, 1, 1);

        when(merchantRepository.findByFilters(eq("ACTIVE"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        GenerateResult result = settlementService.generateSettlements(from, to);

        assertThat(result.getBatchesCreated()).isEqualTo(0);
        assertThat(result.getBatchesSkipped()).isEqualTo(0);
    }

    // ── listBatches ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("listBatches returns page of batch responses")
    void listBatches_returnsMappedPage() {
        SettlementBatch batch = buildBatch();
        when(batchRepository.findByFilters(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(batch)));

        SettlementFilter filter = new SettlementFilter();
        PageResponse<SettlementBatchResponse> result = settlementService.listBatches(filter);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMerchantId()).isEqualTo("MRC-001");
    }

    // ── getBatch ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getBatch returns response with items when found")
    void getBatch_found_returnsResponseWithItems() {
        SettlementBatch batch = buildBatch();
        SettlementItem item = buildItem(batch);
        batch.getItems().add(item);

        when(batchRepository.findBySettlementRefWithItems(batch.getSettlementRef()))
                .thenReturn(Optional.of(batch));

        SettlementBatchResponse response = settlementService.getBatch(batch.getSettlementRef());

        assertThat(response.getMerchantId()).isEqualTo("MRC-001");
        assertThat(response.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("getBatch throws ResourceNotFoundException when not found")
    void getBatch_notFound_throws() {
        UUID ref = UUID.randomUUID();
        when(batchRepository.findBySettlementRefWithItems(ref)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.getBatch(ref))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Payment buildPayment() {
        return Payment.builder()
                .id(1L)
                .paymentRef(UUID.randomUUID())
                .merchant(merchant)
                .orderId("ORDER-001")
                .status("SUCCESS")
                .amount(new BigDecimal("10000"))
                .currency("NGN")
                .channel("CARD")
                .msc(new BigDecimal("200"))
                .vatAmount(new BigDecimal("15"))
                .processorFee(new BigDecimal("100"))
                .processorVat(new BigDecimal("7.5"))
                .payableVat(new BigDecimal("7.5"))
                .amountPayable(new BigDecimal("9785"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private SettlementBatch buildBatch() {
        return SettlementBatch.builder()
                .id(1L)
                .merchant(merchant)
                .periodStart(LocalDate.of(2024, 1, 1))
                .periodEnd(LocalDate.of(2024, 1, 1))
                .count(1)
                .transactionAmount(new BigDecimal("10000"))
                .msc(new BigDecimal("200"))
                .vatAmount(new BigDecimal("15"))
                .processorFee(new BigDecimal("100"))
                .processorVat(new BigDecimal("7.5"))
                .income(new BigDecimal("100"))
                .payableVat(new BigDecimal("7.5"))
                .amountPayable(new BigDecimal("9785"))
                .status("PENDING")
                .createdAt(Instant.now())
                .build();
    }

    private SettlementItem buildItem(SettlementBatch batch) {
        Payment payment = buildPayment();
        return SettlementItem.builder()
                .id(1L)
                .batch(batch)
                .payment(payment)
                .amount(payment.getAmount())
                .msc(payment.getMsc())
                .vatAmount(payment.getVatAmount())
                .processorFee(payment.getProcessorFee())
                .processorVat(payment.getProcessorVat())
                .amountPayable(payment.getAmountPayable())
                .build();
    }
}
