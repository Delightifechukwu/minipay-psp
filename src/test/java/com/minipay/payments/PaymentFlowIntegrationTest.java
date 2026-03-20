package com.minipay.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.auth.dto.AuthDtos.LoginRequest;
import com.minipay.auth.dto.AuthDtos.TokenResponse;
import com.minipay.merchants.dto.MerchantDtos.*;
import com.minipay.payments.dto.PaymentDtos.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Payment Flow Integration Test")
class PaymentFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("minipay_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    // Shared state across ordered tests
    static String adminToken;
    static String createdMerchantId;
    static UUID  createdPaymentRef;

    // ── 1. Login ──────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("Admin can log in and get JWT")
    void adminLogin() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Admin@123");

        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        TokenResponse token = mapper.readValue(
                result.getResponse().getContentAsString(), TokenResponse.class);
        adminToken = token.getAccessToken();
        assertThat(adminToken).isNotBlank();
    }

    // ── 2. Create merchant ────────────────────────────────────────────────────

    @Test @Order(2)
    @DisplayName("Admin can create a merchant")
    void createMerchant() throws Exception {
        CreateMerchantRequest req = new CreateMerchantRequest();
        req.setName("Acme Retail");
        req.setEmail("acme@minipay-test.com");
        req.setSettlementAccount("0123456789");
        req.setSettlementBank("First Bank");
        req.setCallbackUrl("https://acme.example.com/webhook");

        MvcResult result = mvc.perform(post("/api/merchants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchantId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        MerchantResponse merchant = mapper.readValue(
                result.getResponse().getContentAsString(), MerchantResponse.class);
        createdMerchantId = merchant.getMerchantId();
        assertThat(createdMerchantId).startsWith("MRC-");
    }

    // ── 3. Configure charge settings ─────────────────────────────────────────

    @Test @Order(3)
    @DisplayName("Admin can configure charge settings for merchant")
    void configureChargeSettings() throws Exception {
        ChargeSettingRequest req = new ChargeSettingRequest();
        req.setPercentageFee(new BigDecimal("1.5"));
        req.setFixedFee(new BigDecimal("50"));
        req.setUseFixedMsc(false);
        req.setMscCap(new BigDecimal("2000"));
        req.setVatRate(new BigDecimal("7.5"));
        req.setPlatformProviderRate(new BigDecimal("1.0"));
        req.setPlatformProviderCap(new BigDecimal("1200"));

        mvc.perform(put("/api/merchants/{id}/charge-settings", createdMerchantId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentageFee").value(1.5))
                .andExpect(jsonPath("$.vatRate").value(7.5));
    }

    // ── 4. Initiate payment ───────────────────────────────────────────────────

    @Test @Order(4)
    @DisplayName("Can initiate a payment with correct fee computations")
    void initiatePayment() throws Exception {
        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setMerchantId(createdMerchantId);
        req.setOrderId("ORDER-001");
        req.setAmount(new BigDecimal("100000"));
        req.setCurrency("NGN");
        req.setChannel("CARD");
        req.setCustomerId("CUST-001");

        MvcResult result = mvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "idem-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                // Spec Example 1 assertions
                .andExpect(jsonPath("$.msc").value(1550.00))
                .andExpect(jsonPath("$.vatAmount").value(116.25))
                .andExpect(jsonPath("$.processorFee").value(1000.00))
                .andExpect(jsonPath("$.processorVat").value(75.00))
                .andExpect(jsonPath("$.amountPayable").value(97258.75))
                .andReturn();

        PaymentResponse payment = mapper.readValue(
                result.getResponse().getContentAsString(), PaymentResponse.class);
        createdPaymentRef = payment.getPaymentRef();
        assertThat(createdPaymentRef).isNotNull();
    }

    // ── 5. Idempotency — same key returns same payment ────────────────────────

    @Test @Order(5)
    @DisplayName("Idempotency key returns same PENDING payment on retry")
    void idempotencyReturnsSamePendingPayment() throws Exception {
        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setMerchantId(createdMerchantId);
        req.setOrderId("ORDER-001");
        req.setAmount(new BigDecimal("100000"));
        req.setCurrency("NGN");
        req.setChannel("CARD");

        mvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "idem-key-001") // same key
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentRef").value(createdPaymentRef.toString()));
    }

    // ── 6. Simulate SUCCESS callback ─────────────────────────────────────────

    @Test @Order(6)
    @DisplayName("Processor callback transitions payment to SUCCESS")
    void simulateSuccessCallback() throws Exception {
        SimulateCallbackRequest req = new SimulateCallbackRequest();
        req.setPaymentRef(createdPaymentRef);
        req.setStatus("SUCCESS");

        mvc.perform(post("/api/simulate/processor-callback")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    // ── 7. Get payment — verify SUCCESS ──────────────────────────────────────

    @Test @Order(7)
    @DisplayName("Payment status persisted as SUCCESS after callback")
    void getPaymentVerifySuccess() throws Exception {
        mvc.perform(get("/api/payments/{ref}", createdPaymentRef)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.amountPayable").value(97258.75));
    }

    // ── 8. Double-transition guard ────────────────────────────────────────────

    @Test @Order(8)
    @DisplayName("Cannot transition a SUCCESS payment again")
    void cannotTransitionTwice() throws Exception {
        SimulateCallbackRequest req = new SimulateCallbackRequest();
        req.setPaymentRef(createdPaymentRef);
        req.setStatus("FAILED");

        mvc.perform(post("/api/simulate/processor-callback")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Business Rule Violation"));
    }

    // ── 9. Settlement generation ──────────────────────────────────────────────

    @Test @Order(9)
    @DisplayName("Settlement generation creates batch for successful payments")
    void generateSettlement() throws Exception {
        String today = java.time.LocalDate.now().toString();

        mvc.perform(post("/api/settlements/generate")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("from", today)
                        .param("to", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchesCreated").value(1))
                .andExpect(jsonPath("$.totalTransactions").value(1));
    }

    // ── 10. Reports ───────────────────────────────────────────────────────────

    @Test @Order(10)
    @DisplayName("Transaction report returns paginated results")
    void transactionReport() throws Exception {
        mvc.perform(get("/api/reports/transactions")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("merchantId", createdMerchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));
    }

    @Test @Order(11)
    @DisplayName("Transaction report CSV export returns 200 with correct content-type")
    void transactionCsvExport() throws Exception {
        mvc.perform(get("/api/reports/transactions")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("format", "CSV"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("transactions.csv")));
    }
}
