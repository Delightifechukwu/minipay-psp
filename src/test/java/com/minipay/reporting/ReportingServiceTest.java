package com.minipay.reporting;

import com.minipay.common.PageResponse;
import com.minipay.merchants.domain.Merchant;
import com.minipay.payments.domain.Payment;
import com.minipay.payments.dto.PaymentDtos.PaymentResponse;
import com.minipay.payments.repo.PaymentRepository;
import com.minipay.payments.service.PaymentService;
import com.minipay.reporting.dto.ReportDtos.*;
import com.minipay.reporting.service.ReportingService;
import com.minipay.settlements.domain.SettlementBatch;
import com.minipay.settlements.dto.SettlementDtos.SettlementBatchResponse;
import com.minipay.settlements.repo.SettlementBatchRepository;
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
@DisplayName("ReportingService Unit Tests")
class ReportingServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock SettlementBatchRepository batchRepository;
    @Mock PaymentService paymentService;
    @Mock SettlementService settlementService;

    @InjectMocks ReportingService reportingService;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = Merchant.builder()
                .id(1L).merchantId("MRC-001").name("Test").email("t@t.com")
                .status("ACTIVE").settlementAccount("001").settlementBank("GTB")
                .webhookSecret("secret")
                .build();
    }

    // ── getTransactionReport ──────────────────────────────────────────────────

    @Test
    @DisplayName("getTransactionReport returns paged results")
    void getTransactionReport_returnsPage() {
        Payment payment = buildPayment();
        when(paymentRepository.findByFilters(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(payment)));
        when(paymentService.toResponse(any())).thenReturn(new PaymentResponse());

        TransactionReportFilter filter = new TransactionReportFilter();
        PageResponse<?> result = reportingService.getTransactionReport(filter);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getTransactionReport uses sentinel instants when from/to are null")
    void getTransactionReport_nullDates_usesSentinels() {
        when(paymentRepository.findByFilters(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        TransactionReportFilter filter = new TransactionReportFilter();
        filter.setFrom(null);
        filter.setTo(null);

        reportingService.getTransactionReport(filter);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor   = ArgumentCaptor.forClass(Instant.class);
        verify(paymentRepository).findByFilters(any(), any(), any(), fromCaptor.capture(), toCaptor.capture(), any());
        assertThat(fromCaptor.getValue()).isEqualTo(Instant.EPOCH);
        assertThat(toCaptor.getValue()).isAfter(Instant.now());
    }

    // ── getSettlementReport ───────────────────────────────────────────────────

    @Test
    @DisplayName("getSettlementReport returns paged settlement batches")
    void getSettlementReport_returnsPage() {
        SettlementBatch batch = buildBatch();
        when(batchRepository.findByFilters(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(batch)));
        when(settlementService.toResponse(any(), eq(false))).thenReturn(new SettlementBatchResponse());

        SettlementReportFilter filter = new SettlementReportFilter();
        PageResponse<?> result = reportingService.getSettlementReport(filter);

        assertThat(result.getContent()).hasSize(1);
    }

    // ── exportTransactionsCsv ─────────────────────────────────────────────────

    @Test
    @DisplayName("exportTransactionsCsv returns non-empty byte array with header")
    void exportTransactionsCsv_returnsBytes() throws Exception {
        Payment payment = buildPayment();
        when(paymentRepository.findForExport(any(), any(), any(), any(), any()))
                .thenReturn(List.of(payment));

        TransactionReportFilter filter = new TransactionReportFilter();
        byte[] csv = reportingService.exportTransactionsCsv(filter);

        assertThat(csv).isNotEmpty();
        String content = new String(csv);
        assertThat(content).contains("PaymentRef");
        assertThat(content).contains("ORDER-001");
    }

    @Test
    @DisplayName("exportTransactionsCsv with no data returns only header")
    void exportTransactionsCsv_noData_returnsHeaderOnly() throws Exception {
        when(paymentRepository.findForExport(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        byte[] csv = reportingService.exportTransactionsCsv(new TransactionReportFilter());

        assertThat(csv).isNotEmpty();
        assertThat(new String(csv)).contains("PaymentRef");
    }

    // ── exportSettlementsCsv ──────────────────────────────────────────────────

    @Test
    @DisplayName("exportSettlementsCsv returns non-empty byte array")
    void exportSettlementsCsv_returnsBytes() throws Exception {
        SettlementBatch batch = buildBatch();
        when(batchRepository.findByFilters(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(batch)));

        SettlementReportFilter filter = new SettlementReportFilter();
        byte[] csv = reportingService.exportSettlementsCsv(filter);

        assertThat(csv).isNotEmpty();
        String content = new String(csv);
        assertThat(content).contains("SettlementRef");
        assertThat(content).contains("MRC-001");
    }

    // ── exportTransactionsExcel ───────────────────────────────────────────────

    @Test
    @DisplayName("exportTransactionsExcel returns valid xlsx bytes")
    void exportTransactionsExcel_returnsBytes() throws Exception {
        Payment payment = buildPayment();
        when(paymentRepository.findForExport(any(), any(), any(), any(), any()))
                .thenReturn(List.of(payment));

        byte[] xlsx = reportingService.exportTransactionsExcel(new TransactionReportFilter());

        assertThat(xlsx).isNotEmpty();
        // XLSX files start with PK (zip magic bytes)
        assertThat(xlsx[0]).isEqualTo((byte) 0x50); // 'P'
        assertThat(xlsx[1]).isEqualTo((byte) 0x4B); // 'K'
    }

    @Test
    @DisplayName("exportTransactionsExcel with empty data returns valid xlsx")
    void exportTransactionsExcel_noData_returnsValidXlsx() throws Exception {
        when(paymentRepository.findForExport(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        byte[] xlsx = reportingService.exportTransactionsExcel(new TransactionReportFilter());

        assertThat(xlsx).isNotEmpty();
        assertThat(xlsx[0]).isEqualTo((byte) 0x50);
    }

    // ── exportSettlementsExcel ────────────────────────────────────────────────

    @Test
    @DisplayName("exportSettlementsExcel returns valid xlsx bytes")
    void exportSettlementsExcel_returnsBytes() throws Exception {
        SettlementBatch batch = buildBatch();
        when(batchRepository.findByFilters(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(batch)));

        byte[] xlsx = reportingService.exportSettlementsExcel(new SettlementReportFilter());

        assertThat(xlsx).isNotEmpty();
        assertThat(xlsx[0]).isEqualTo((byte) 0x50);
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
                .periodEnd(LocalDate.of(2024, 1, 31))
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
}
