package com.minipay.merchants;

import com.minipay.merchants.domain.Merchant;
import com.minipay.merchants.repo.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisplayName("MerchantRepository slice tests")
class MerchantRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("minipay_slice")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired TestEntityManager em;
    @Autowired MerchantRepository repo;

    private Merchant saved;

    @BeforeEach
    void setup() {
        saved = repo.save(Merchant.builder()
                .merchantId("MRC-TEST01")
                .name("Test Merchant")
                .email("test@example.com")
                .status("ACTIVE")
                .settlementAccount("0123456789")
                .settlementBank("Test Bank")
                .webhookSecret("secret123")
                .build());
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findByMerchantId returns merchant")
    void findByMerchantId() {
        Optional<Merchant> result = repo.findByMerchantId("MRC-TEST01");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Merchant");
    }

    @Test
    @DisplayName("existsByEmail detects duplicates")
    void existsByEmail() {
        assertThat(repo.existsByEmail("test@example.com")).isTrue();
        assertThat(repo.existsByEmail("other@example.com")).isFalse();
    }

    @Test
    @DisplayName("findByFilters filters by status")
    void filterByStatus() {
        Page<Merchant> active = repo.findByFilters("ACTIVE", null, PageRequest.of(0, 10));
        assertThat(active.getTotalElements()).isGreaterThanOrEqualTo(1);

        Page<Merchant> suspended = repo.findByFilters("SUSPENDED", null, PageRequest.of(0, 10));
        assertThat(suspended.getContent()).noneMatch(m -> "MRC-TEST01".equals(m.getMerchantId()));
    }

    @Test
    @DisplayName("findByFilters filters by name (case-insensitive)")
    void filterByName() {
        Page<Merchant> result = repo.findByFilters(null, "test", PageRequest.of(0, 10));
        assertThat(result.getContent()).anyMatch(m -> "MRC-TEST01".equals(m.getMerchantId()));
    }
}
