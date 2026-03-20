package com.minipay.merchants.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.common.PageResponse;
import com.minipay.common.audit.*;
import com.minipay.common.errors.*;
import com.minipay.merchants.domain.*;
import com.minipay.merchants.dto.MerchantDtos.*;
import com.minipay.merchants.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final ChargeSettingRepository chargeSettingRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Object createMerchant(CreateMerchantRequest request) {
        String actor = currentUsername();
        boolean isMaker = hasRole("ROLE_MAKER");

        // MAKER role requires CHECKER approval
        if (isMaker) {
            return submitForApproval("MERCHANT", null, "CREATE", request, actor);
        }

        return doCreateMerchant(request, actor);
    }

    @Transactional
    public MerchantResponse doCreateMerchant(CreateMerchantRequest request, String actor) {
        if (merchantRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Merchant email already registered: " + request.getEmail());
        }

        String merchantId = generateMerchantId();
        String webhookSecret = UUID.randomUUID().toString().replace("-", "");

        Merchant merchant = Merchant.builder()
                .merchantId(merchantId)
                .name(request.getName())
                .email(request.getEmail())
                .settlementAccount(request.getSettlementAccount())
                .settlementBank(request.getSettlementBank())
                .callbackUrl(request.getCallbackUrl())
                .webhookSecret(webhookSecret)
                .status("ACTIVE")
                .build();

        merchant = merchantRepository.save(merchant);
        auditLog("MERCHANT", merchantId, "CREATE", actor, null, toJson(merchant));

        log.info("Merchant created: {} by {}", merchantId, actor);
        return toResponse(merchant);
    }

    @Transactional
    public MerchantResponse updateMerchant(String merchantId, UpdateMerchantRequest request) {
        String actor = currentUsername();
        Merchant merchant = merchantRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", merchantId));

        String oldValue = toJson(merchant);

        if (request.getName() != null) merchant.setName(request.getName());
        if (request.getEmail() != null) {
            if (!request.getEmail().equals(merchant.getEmail()) &&
                    merchantRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("Email already in use");
            }
            merchant.setEmail(request.getEmail());
        }
        if (request.getSettlementAccount() != null) merchant.setSettlementAccount(request.getSettlementAccount());
        if (request.getSettlementBank() != null) merchant.setSettlementBank(request.getSettlementBank());
        if (request.getCallbackUrl() != null) merchant.setCallbackUrl(request.getCallbackUrl());
        if (request.getStatus() != null) merchant.setStatus(request.getStatus());

        merchant = merchantRepository.save(merchant);
        auditLog("MERCHANT", merchantId, "UPDATE", actor, oldValue, toJson(merchant));

        return toResponse(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchant(String merchantId) {
        return toResponse(findOrThrow(merchantId));
    }

    @Transactional(readOnly = true)
    public PageResponse<MerchantResponse> listMerchants(MerchantFilter filter) {
        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(filter.getSortDir()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                filter.getSortBy()
        );
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);
        Page<Merchant> page = merchantRepository.findByFilters(filter.getStatus(), filter.getName(), pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    @Transactional
    public ChargeSettingResponse upsertChargeSetting(String merchantId, ChargeSettingRequest request) {
        String actor = currentUsername();
        Merchant merchant = findOrThrow(merchantId);

        ChargeSetting setting = chargeSettingRepository.findByMerchant(merchant)
                .orElse(ChargeSetting.builder().merchant(merchant).build());

        String oldValue = setting.getId() != null ? toJson(setting) : null;

        setting.setPercentageFee(request.getPercentageFee());
        setting.setFixedFee(request.getFixedFee());
        setting.setUseFixedMSC(request.getUseFixedMsc());
        setting.setMscCap(request.getMscCap());
        setting.setVatRate(request.getVatRate());
        setting.setPlatformProviderRate(request.getPlatformProviderRate());
        setting.setPlatformProviderCap(request.getPlatformProviderCap());

        setting = chargeSettingRepository.save(setting);
        auditLog("CHARGE_SETTING", merchantId, "UPSERT", actor, oldValue, toJson(setting));

        return toChargeResponse(setting);
    }

    @Transactional(readOnly = true)
    public ChargeSettingResponse getChargeSetting(String merchantId) {
        Merchant merchant = findOrThrow(merchantId);
        ChargeSetting setting = chargeSettingRepository.findByMerchant(merchant)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Charge settings not configured for merchant: " + merchantId));
        return toChargeResponse(setting);
    }

    // ── Approval workflow ────────────────────────────────────────────────────

    @Transactional
    public MerchantResponse approveRequest(UUID requestRef, String checkerNote) {
        ApprovalRequest approval = approvalRequestRepository.findByRequestRef(requestRef)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", requestRef.toString()));

        if (!"PENDING".equals(approval.getStatus())) {
            throw new BusinessException("Request is not in PENDING state");
        }

        String checker = currentUsername();
        if (checker.equals(approval.getMakerUsername())) {
            throw new BusinessException("Maker cannot approve their own request");
        }

        try {
            CreateMerchantRequest req = objectMapper.readValue(
                    approval.getPayload(), CreateMerchantRequest.class);
            MerchantResponse result = doCreateMerchant(req, approval.getMakerUsername());

            approval.setStatus("APPROVED");
            approval.setCheckerUsername(checker);
            approval.setCheckerNote(checkerNote);
            approval.setReviewedAt(java.time.Instant.now());
            approvalRequestRepository.save(approval);

            return result;
        } catch (Exception e) {
            throw new BusinessException("Failed to execute approved request: " + e.getMessage());
        }
    }

    @Transactional
    public void rejectRequest(UUID requestRef, String checkerNote) {
        ApprovalRequest approval = approvalRequestRepository.findByRequestRef(requestRef)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", requestRef.toString()));

        if (!"PENDING".equals(approval.getStatus())) {
            throw new BusinessException("Request is not in PENDING state");
        }

        String checker = currentUsername();
        if (checker.equals(approval.getMakerUsername())) {
            throw new BusinessException("Maker cannot reject their own request");
        }

        approval.setStatus("REJECTED");
        approval.setCheckerUsername(checker);
        approval.setCheckerNote(checkerNote);
        approval.setReviewedAt(java.time.Instant.now());
        approvalRequestRepository.save(approval);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    public Merchant findOrThrow(String merchantId) {
        return merchantRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", merchantId));
    }

    private ApprovalRequiredException submitForApproval(
            String entityType, String entityId, String action, Object payload, String maker) {
        try {
            ApprovalRequest req = ApprovalRequest.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .payload(objectMapper.writeValueAsString(payload))
                    .makerUsername(maker)
                    .build();
            ApprovalRequest saved = approvalRequestRepository.save(req);
            return new ApprovalRequiredException(saved.getRequestRef().toString());
        } catch (Exception e) {
            throw new BusinessException("Failed to submit approval request");
        }
    }

    private void auditLog(String entityType, String entityId, String action,
                           String actor, String oldVal, String newVal) {
        AuditLog log = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .actorUsername(actor)
                .oldValue(oldVal)
                .newValue(newVal)
                .correlationId(MDC.get("correlationId"))
                .build();
        auditLogRepository.save(log);
    }

    private String generateMerchantId() {
        return "MRC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean hasRole(String role) {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    public MerchantResponse toResponse(Merchant m) {
        MerchantResponse r = new MerchantResponse();
        r.setId(m.getId());
        r.setMerchantId(m.getMerchantId());
        r.setName(m.getName());
        r.setEmail(m.getEmail());
        r.setStatus(m.getStatus());
        r.setSettlementAccount(m.getSettlementAccount());
        r.setSettlementBank(m.getSettlementBank());
        r.setCallbackUrl(m.getCallbackUrl());
        r.setCreatedAt(m.getCreatedAt());
        r.setUpdatedAt(m.getUpdatedAt());
        return r;
    }

    private ChargeSettingResponse toChargeResponse(ChargeSetting s) {
        ChargeSettingResponse r = new ChargeSettingResponse();
        r.setId(s.getId());
        r.setPercentageFee(s.getPercentageFee());
        r.setFixedFee(s.getFixedFee());
        r.setUseFixedMsc(s.getUseFixedMSC());
        r.setMscCap(s.getMscCap());
        r.setVatRate(s.getVatRate());
        r.setPlatformProviderRate(s.getPlatformProviderRate());
        r.setPlatformProviderCap(s.getPlatformProviderCap());
        r.setUpdatedAt(s.getUpdatedAt());
        return r;
    }
}
