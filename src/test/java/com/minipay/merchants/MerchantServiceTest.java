package com.minipay.merchants;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.common.audit.ApprovalRequest;
import com.minipay.common.audit.ApprovalRequestRepository;
import com.minipay.common.audit.AuditLogRepository;
import com.minipay.common.errors.BusinessException;
import com.minipay.common.errors.ConflictException;
import com.minipay.common.errors.ResourceNotFoundException;
import com.minipay.merchants.domain.ChargeSetting;
import com.minipay.merchants.domain.Merchant;
import com.minipay.merchants.dto.MerchantDtos.*;
import com.minipay.merchants.repo.ChargeSettingRepository;
import com.minipay.merchants.repo.MerchantRepository;
import com.minipay.merchants.service.MerchantService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantService Unit Tests")
class MerchantServiceTest {

    @Mock MerchantRepository merchantRepository;
    @Mock ChargeSettingRepository chargeSettingRepository;
    @Mock ApprovalRequestRepository approvalRequestRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks MerchantService merchantService;

    private Merchant testMerchant;

    @BeforeEach
    void setUp() {
        testMerchant = Merchant.builder()
                .id(1L)
                .merchantId("MRC-TEST01")
                .name("Test Merchant")
                .email("test@merchant.com")
                .status("ACTIVE")
                .settlementAccount("0123456789")
                .settlementBank("First Bank")
                .webhookSecret("secret")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Set up security context with ADMIN
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    // ── getMerchant ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMerchant returns response when found")
    void getMerchant_found_returnsResponse() {
        when(merchantRepository.findByMerchantId("MRC-TEST01"))
                .thenReturn(Optional.of(testMerchant));

        MerchantResponse result = merchantService.getMerchant("MRC-TEST01");

        assertThat(result.getMerchantId()).isEqualTo("MRC-TEST01");
        assertThat(result.getName()).isEqualTo("Test Merchant");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("getMerchant throws ResourceNotFoundException when not found")
    void getMerchant_notFound_throws() {
        when(merchantRepository.findByMerchantId("NONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.getMerchant("NONE"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── listMerchants ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("listMerchants returns paginated results")
    void listMerchants_returnsPaginatedResults() {
        Page<Merchant> page = new PageImpl<>(List.of(testMerchant));
        when(merchantRepository.findByFilters(any(), any(), any())).thenReturn(page);

        MerchantFilter filter = new MerchantFilter();
        filter.setPage(0);
        filter.setSize(10);
        filter.setSortBy("name");
        filter.setSortDir("asc");

        var result = merchantService.listMerchants(filter);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMerchantId()).isEqualTo("MRC-TEST01");
    }

    // ── updateMerchant ────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateMerchant updates fields and returns response")
    void updateMerchant_success() {
        when(merchantRepository.findByMerchantId("MRC-TEST01"))
                .thenReturn(Optional.of(testMerchant));
        when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateMerchantRequest req = new UpdateMerchantRequest();
        req.setName("Updated Name");
        req.setStatus("INACTIVE");

        MerchantResponse result = merchantService.updateMerchant("MRC-TEST01", req);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("updateMerchant throws when merchant not found")
    void updateMerchant_notFound_throws() {
        when(merchantRepository.findByMerchantId("NONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.updateMerchant("NONE", new UpdateMerchantRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateMerchant throws ConflictException when new email already taken")
    void updateMerchant_emailConflict_throws() {
        when(merchantRepository.findByMerchantId("MRC-TEST01"))
                .thenReturn(Optional.of(testMerchant));
        when(merchantRepository.existsByEmail("taken@test.com")).thenReturn(true);

        UpdateMerchantRequest req = new UpdateMerchantRequest();
        req.setEmail("taken@test.com");

        assertThatThrownBy(() -> merchantService.updateMerchant("MRC-TEST01", req))
                .isInstanceOf(ConflictException.class);
    }

    // ── getChargeSetting ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getChargeSetting returns charge setting when exists")
    void getChargeSetting_found_returnsResponse() {
        when(merchantRepository.findByMerchantId("MRC-TEST01"))
                .thenReturn(Optional.of(testMerchant));
        ChargeSetting setting = ChargeSetting.builder()
                .id(1L).merchant(testMerchant)
                .percentageFee(new BigDecimal("1.5"))
                .fixedFee(new BigDecimal("50"))
                .mscCap(new BigDecimal("2000"))
                .vatRate(new BigDecimal("7.5"))
                .platformProviderRate(new BigDecimal("1.0"))
                .platformProviderCap(new BigDecimal("1200"))
                .useFixedMSC(false)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(chargeSettingRepository.findByMerchant(testMerchant))
                .thenReturn(Optional.of(setting));

        ChargeSettingResponse result = merchantService.getChargeSetting("MRC-TEST01");

        assertThat(result.getPercentageFee()).isEqualByComparingTo("1.5");
        assertThat(result.getVatRate()).isEqualByComparingTo("7.5");
    }

    @Test
    @DisplayName("getChargeSetting throws when not configured")
    void getChargeSetting_notConfigured_throws() {
        when(merchantRepository.findByMerchantId("MRC-TEST01"))
                .thenReturn(Optional.of(testMerchant));
        when(chargeSettingRepository.findByMerchant(testMerchant))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.getChargeSetting("MRC-TEST01"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── doCreateMerchant ──────────────────────────────────────────────────────

    @Test
    @DisplayName("doCreateMerchant creates and returns merchant")
    void doCreateMerchant_success() {
        when(merchantRepository.existsByEmail("new@merchant.com")).thenReturn(false);
        when(merchantRepository.save(any())).thenAnswer(inv -> {
            Merchant m = inv.getArgument(0);
            m.setId(2L);
            m.setCreatedAt(Instant.now());
            m.setUpdatedAt(Instant.now());
            return m;
        });

        CreateMerchantRequest req = new CreateMerchantRequest();
        req.setName("New Merchant");
        req.setEmail("new@merchant.com");
        req.setSettlementAccount("9876543210");
        req.setSettlementBank("GTB");
        req.setCallbackUrl("https://callback.example.com");

        MerchantResponse result = merchantService.doCreateMerchant(req, "admin");

        assertThat(result.getName()).isEqualTo("New Merchant");
        assertThat(result.getMerchantId()).startsWith("MRC-");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("doCreateMerchant throws when email already registered")
    void doCreateMerchant_duplicateEmail_throws() {
        when(merchantRepository.existsByEmail("test@merchant.com")).thenReturn(true);

        CreateMerchantRequest req = new CreateMerchantRequest();
        req.setEmail("test@merchant.com");

        assertThatThrownBy(() -> merchantService.doCreateMerchant(req, "admin"))
                .isInstanceOf(ConflictException.class);
    }

    // ── rejectRequest ────────────────────────────────────────────────────────

    @Test
    @DisplayName("rejectRequest marks approval as REJECTED")
    void rejectRequest_success() {
        UUID ref = UUID.randomUUID();
        ApprovalRequest approval = ApprovalRequest.builder()
                .requestRef(ref)
                .status("PENDING")
                .makerUsername("maker")
                .build();
        when(approvalRequestRepository.findByRequestRef(ref))
                .thenReturn(Optional.of(approval));
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        merchantService.rejectRequest(ref, "Not acceptable");

        assertThat(approval.getStatus()).isEqualTo("REJECTED");
        assertThat(approval.getCheckerUsername()).isEqualTo("admin");
        assertThat(approval.getCheckerNote()).isEqualTo("Not acceptable");
    }

    @Test
    @DisplayName("rejectRequest throws when request not found")
    void rejectRequest_notFound_throws() {
        UUID ref = UUID.randomUUID();
        when(approvalRequestRepository.findByRequestRef(ref)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.rejectRequest(ref, "note"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("rejectRequest throws when request not in PENDING state")
    void rejectRequest_alreadyProcessed_throws() {
        UUID ref = UUID.randomUUID();
        ApprovalRequest approval = ApprovalRequest.builder()
                .requestRef(ref)
                .status("APPROVED")
                .makerUsername("maker")
                .build();
        when(approvalRequestRepository.findByRequestRef(ref))
                .thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> merchantService.rejectRequest(ref, "note"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("rejectRequest throws when checker is same as maker")
    void rejectRequest_selfApproval_throws() {
        UUID ref = UUID.randomUUID();
        ApprovalRequest approval = ApprovalRequest.builder()
                .requestRef(ref)
                .status("PENDING")
                .makerUsername("admin") // same as current user
                .build();
        when(approvalRequestRepository.findByRequestRef(ref))
                .thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> merchantService.rejectRequest(ref, "note"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Maker cannot reject");
    }
}