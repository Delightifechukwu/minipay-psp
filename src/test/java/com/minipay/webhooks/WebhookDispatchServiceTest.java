package com.minipay.webhooks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.merchants.domain.Merchant;
import com.minipay.payments.domain.Payment;
import com.minipay.webhooks.domain.WebhookEvent;
import com.minipay.webhooks.repo.WebhookEventRepository;
import com.minipay.webhooks.service.WebhookDispatchService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookDispatchService Unit Tests")
class WebhookDispatchServiceTest {

    @Mock WebhookEventRepository webhookEventRepository;

    private WebhookDispatchService service;

    @BeforeEach
    void setUp() {
        service = new WebhookDispatchService(webhookEventRepository, new ObjectMapper());
    }

    private Payment buildPayment(String callbackUrl) {
        Merchant merchant = Merchant.builder()
                .id(1L).merchantId("MRC-001").webhookSecret("test-secret")
                .name("Test").email("m@test.com")
                .settlementAccount("00001").settlementBank("GTB")
                .webhookSecret("secret")
                .build();

        return Payment.builder()
                .id(1L)
                .paymentRef(UUID.randomUUID())
                .merchant(merchant)
                .orderId("ORDER-001")
                .status("SUCCESS")
                .amount(new BigDecimal("5000.00"))
                .currency("NGN")
                .channel("CARD")
                .msc(new BigDecimal("75.00"))
                .vatAmount(new BigDecimal("5.63"))
                .processorFee(new BigDecimal("50.00"))
                .processorVat(new BigDecimal("3.75"))
                .payableVat(new BigDecimal("1.88"))
                .amountPayable(new BigDecimal("4919.37"))
                .callbackUrl(callbackUrl)
                .build();
    }

    @Test
    @DisplayName("enqueue with callback URL saves webhook event")
    void enqueue_withCallbackUrl_savesEvent() {
        Payment payment = buildPayment("https://merchant.example.com/webhook");
        when(webhookEventRepository.save(any())).thenReturn(new WebhookEvent());

        service.enqueue(payment);

        ArgumentCaptor<WebhookEvent> captor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(captor.capture());
        WebhookEvent saved = captor.getValue();
        assertThat(saved.getTargetUrl()).isEqualTo("https://merchant.example.com/webhook");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getPayload()).contains("ORDER-001");
        assertThat(saved.getPayload()).contains("SUCCESS");
    }

    @Test
    @DisplayName("enqueue without callback URL skips webhook")
    void enqueue_withoutCallbackUrl_skipsWebhook() {
        Payment payment = buildPayment(null);

        service.enqueue(payment);

        verifyNoInteractions(webhookEventRepository);
    }

    @Test
    @DisplayName("enqueue with blank callback URL skips webhook")
    void enqueue_withBlankCallbackUrl_skipsWebhook() {
        Payment payment = buildPayment("  ");

        service.enqueue(payment);

        verifyNoInteractions(webhookEventRepository);
    }

    @Test
    @DisplayName("enqueue payload contains all required fields")
    void enqueue_payloadContainsRequiredFields() {
        Payment payment = buildPayment("https://callback.test/wh");
        when(webhookEventRepository.save(any())).thenReturn(new WebhookEvent());

        service.enqueue(payment);

        ArgumentCaptor<WebhookEvent> captor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(captor.capture());
        String payload = captor.getValue().getPayload();
        assertThat(payload)
                .contains("paymentRef")
                .contains("orderId")
                .contains("status")
                .contains("amount")
                .contains("currency")
                .contains("amountPayable")
                .contains("timestamp");
    }
}