package com.minipay.auth;

import com.minipay.auth.domain.Role;
import com.minipay.auth.domain.User;
import com.minipay.auth.service.JwtService;
import com.minipay.config.JwtProperties;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private static final String SECRET =
            "test-secret-key-that-is-at-least-256-bits-long-for-testing-purposes-only";

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setTtlMinutes(30);
        props.setRefreshTtlHours(24);
        jwtService = new JwtService(props);

        Role adminRole = new Role(1L, "ADMIN");
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@test.com")
                .passwordHash("hashed")
                .status("ACTIVE")
                .roles(Set.of(adminRole))
                .build();
    }

    @Test
    @DisplayName("Generated token contains correct username")
    void generateToken_extractsCorrectUsername() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.extractUsername(token)).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Generated token contains ROLE_ADMIN authority")
    void generateToken_containsRoles() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.extractRoles(token)).contains("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Token is valid for correct username")
    void isTokenValid_correctUsername_returnsTrue() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.isTokenValid(token, "testuser")).isTrue();
    }

    @Test
    @DisplayName("Token is invalid for wrong username")
    void isTokenValid_wrongUsername_returnsFalse() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.isTokenValid(token, "otheruser")).isFalse();
    }

    @Test
    @DisplayName("Malformed token string returns false")
    void isTokenValid_malformedToken_returnsFalse() {
        assertThat(jwtService.isTokenValid("not.a.jwt", "testuser")).isFalse();
    }

    @Test
    @DisplayName("Expired token (ttl=0) returns false")
    void isTokenValid_expiredToken_returnsFalse() throws Exception {
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret(SECRET);
        shortProps.setTtlMinutes(0); // expires immediately
        shortProps.setRefreshTtlHours(24);
        JwtService shortLivedJwt = new JwtService(shortProps);

        String token = shortLivedJwt.generateAccessToken(testUser);
        Thread.sleep(50); // ensure expiry passes
        assertThat(shortLivedJwt.isTokenValid(token, "testuser")).isFalse();
    }

    @Test
    @DisplayName("Multiple roles are all included in token")
    void generateToken_multipleRoles_allIncluded() {
        Role makerRole = new Role(2L, "MAKER");
        User multiRoleUser = User.builder()
                .id(2L).username("maker").email("maker@test.com")
                .passwordHash("h").status("ACTIVE")
                .roles(Set.of(new Role(1L, "ADMIN"), makerRole))
                .build();

        String token = jwtService.generateAccessToken(multiRoleUser);
        List<String> roles = jwtService.extractRoles(token);
        assertThat(roles).containsAnyOf("ROLE_ADMIN", "ROLE_MAKER");
    }
}