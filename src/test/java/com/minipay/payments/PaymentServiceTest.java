package com.minipay.payments;

import com.minipay.common.errors.BusinessException;
import com.minipay.common.errors.IdempotencyConflictException;
import com.minipay.common.errors.ResourceNotFoundException;
import com.minipay.merchants.domain.ChargeSetting;
import com.minipay.merchants.domain.Merchant;
import com.minipay.merchants.repo.ChargeSettingRepository;
import com.minipay.merchants.service.MerchantService;
import com.minipay.payments.domain.Payment;
import com.minipay.payments.dto.PaymentDtos.*;
import com.minipay.payments.repo.PaymentRepository;
import com.minipay.payments.service.PaymentRateLimiter;
import com.minipay.payments.service.PaymentService;
import com.minipay.webhooks.service.WebhookDispatchService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock MerchantService merchantService;
    @Mock ChargeSettingRepository chargeSettingRepository;
    @Mock WebhookDispatchService webhookDispatchService;
    @Mock PaymentRateLimiter rateLimiter;

    @InjectMocks PaymentService paymentService;

    private Merchant activeMerchant;
    private ChargeSetting chargeSetting;

    @BeforeEach
    void setUp() {
        activeMerchant = Merchant.builder()
                .id(1L).merchantId("MRC-001").name("Test").email("t@t.com")
                .status("ACTIVE").settlementAccount("001").settlementBank("GTB")
                .webhookSecret("secret").callbackUrl("https://cb.example.com")
                .build();

        chargeSetting = ChargeSetting.builder()
                .id(1L).merchant(activeMerchant)
                .percentageFee(new BigDecimal("1.5"))
                .fixedFee(new BigDecimal("50"))
                .mscCap(new BigDecimal("2000"))
                .vatRate(new BigDecimal("7.5"))
                .platformProviderRate(new BigDecimal("1.0"))
                .platformProviderCap(new BigDecimal("1200"))
                .useFixedMSC(false)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    // ── initiatePayment ───────────────────────────────────────────────────────

    @Test
    @DisplayName("initiatePayment succeeds and returns PENDING payment")
    void initiatePayment_success() {
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(merchantService.findOrThrow("MRC-001")).thenReturn(activeMerchant);
        when(chargeSettingRepository.findByMerchant(activeMerchant))
                .thenReturn(Optional.of(chargeSetting));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            p.setCreatedAt(Instant.now());
            p.setUpdatedAt(Instant.now());
            return p;
        });

        InitiatePaymentRequest req = buildPaymentRequest("MRC-001");

        PaymentResponse result = paymentService.initiatePayment(req, "idem-key-001");

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getMerchantId()).isEqualTo("MRC-001");
        assertThat(result.getMsc()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("initiatePayment returns existing payment for same idempotency key in PENDING state")
    void initiatePayment_sameIdempotencyKey_returnsSame() {
        Payment existing = buildExistingPayment("PENDING");
        when(paymentRepository.findByIdempotencyKey("idem-key-001"))
                .thenReturn(Optional.of(existing));

        InitiatePaymentRequest req = buildPaymentRequest("MRC-001");
        PaymentResponse result = paymentService.initiatePayment(req, "idem-key-001");

        assertThat(result.getPaymentRef()).isEqualTo(existing.getPaymentRef());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("initiatePayment throws IdempotencyConflictException when key used for non-PENDING payment")
    void initiatePayment_idempotencyConflict_throws() {
        Payment existing = buildExistingPayment("SUCCESS");
        when(paymentRepository.findByIdempotencyKey("idem-key-002"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.initiatePayment(buildPaymentRequest("MRC-001"), "idem-key-002"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("initiatePayment throws when merchant is INACTIVE")
    void initiatePayment_inactiveMerchant_throws() {
        Merchant inactive = Merchant.builder()
                .id(2L).merchantId("MRC-002").status("INACTIVE")
                .name("Inactive").email("i@i.com")
                .settlementAccount("0").settlementBank("GTB").webhookSecret("s")
                .build();
        when(merchantService.findOrThrow("MRC-002")).thenReturn(inactive);

        assertThatThrownBy(() -> paymentService.initiatePayment(buildPaymentRequest("MRC-002"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("initiatePayment throws when charge settings not configured")
    void initiatePayment_noChargeSettings_throws() {
        when(merchantService.findOrThrow("MRC-001")).thenReturn(activeMerchant);
        when(chargeSettingRepository.findByMerchant(activeMerchant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.initiatePayment(buildPaymentRequest("MRC-001"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Charge settings not configured");
    }

    // ── simulateProcessorCallback ─────────────────────────────────────────────

    @Test
    @DisplayName("simulateCallback transitions PENDING → SUCCESS")
    void simulateCallback_pendingToSuccess() {
        Payment pending = buildExistingPayment("PENDING");
        when(paymentRepository.findByPaymentRefForUpdate(pending.getPaymentRef()))
                .thenReturn(Optional.of(pending));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(webhookDispatchService).enqueue(any());

        SimulateCallbackRequest req = new SimulateCallbackRequest();
        req.setPaymentRef(pending.getPaymentRef());
        req.setStatus("SUCCESS");

        PaymentResponse result = paymentService.simulateProcessorCallback(req);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("simulateCallback throws BusinessException when payment already settled")
    void simulateCallback_alreadySettled_throws() {
        Payment success = buildExistingPayment("SUCCESS");
        when(paymentRepository.findByPaymentRefForUpdate(success.getPaymentRef()))
                .thenReturn(Optional.of(success));

        SimulateCallbackRequest req = new SimulateCallbackRequest();
        req.setPaymentRef(success.getPaymentRef());
        req.setStatus("FAILED");

        assertThatThrownBy(() -> paymentService.simulateProcessorCallback(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SUCCESS");
    }

    @Test
    @DisplayName("simulateCallback throws ResourceNotFoundException when payment not found")
    void simulateCallback_notFound_throws() {
        UUID ref = UUID.randomUUID();
        when(paymentRepository.findByPaymentRefForUpdate(ref)).thenReturn(Optional.empty());

        SimulateCallbackRequest req = new SimulateCallbackRequest();
        req.setPaymentRef(ref);
        req.setStatus("SUCCESS");

        assertThatThrownBy(() -> paymentService.simulateProcessorCallback(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getPayment ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPayment throws ResourceNotFoundException when not found")
    void getPayment_notFound_throws() {
        UUID ref = UUID.randomUUID();
        when(paymentRepository.findByPaymentRef(ref)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(ref))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private InitiatePaymentRequest buildPaymentRequest(String merchantId) {
        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setMerchantId(merchantId);
        req.setOrderId("ORDER-TEST");
        req.setAmount(new BigDecimal("10000"));
        req.setCurrency("NGN");
        req.setChannel("CARD");
        return req;
    }

    private Payment buildExistingPayment(String status) {
        return Payment.builder()
                .id(1L)
                .paymentRef(UUID.randomUUID())
                .merchant(activeMerchant)
                .orderId("ORDER-001")
                .status(status)
                .amount(new BigDecimal("10000"))
                .currency("NGN")
                .channel("CARD")
                .msc(new BigDecimal("200"))
                .vatAmount(new BigDecimal("15"))
                .processorFee(new BigDecimal("100"))
                .processorVat(new BigDecimal("7.5"))
                .payableVat(new BigDecimal("7.5"))
                .amountPayable(new BigDecimal("9785"))
                .callbackUrl("https://cb.example.com")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}