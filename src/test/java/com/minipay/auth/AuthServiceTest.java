package com.minipay.auth;

import com.minipay.auth.domain.RefreshToken;
import com.minipay.auth.domain.Role;
import com.minipay.auth.domain.User;
import com.minipay.auth.dto.AuthDtos.*;
import com.minipay.auth.repo.RefreshTokenRepository;
import com.minipay.auth.repo.RoleRepository;
import com.minipay.auth.repo.UserRepository;
import com.minipay.auth.service.AuthService;
import com.minipay.auth.service.JwtService;
import com.minipay.common.errors.BusinessException;
import com.minipay.common.errors.ConflictException;
import com.minipay.common.errors.RateLimitExceededException;
import com.minipay.config.JwtProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock JwtProperties jwtProperties;
    @Mock AuthenticationManager authenticationManager;
    @Mock StringRedisTemplate redisTemplate;

    @InjectMocks AuthService authService;

    private Role adminRole;
    private User adminUser;

    @BeforeEach
    void setUp() {
        adminRole = new Role(1L, "ADMIN");
        adminUser = User.builder()
                .id(1L).username("admin").email("admin@test.com")
                .passwordHash("hashed").status("ACTIVE")
                .roles(Set.of(adminRole))
                .build();
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Register succeeds with valid unique credentials")
    void register_success() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(roleRepository.findByName("MAKER")).thenReturn(Optional.of(new Role(2L, "MAKER")));
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setCreatedAt(Instant.now());
            u.setUpdatedAt(Instant.now());
            return u;
        });

        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("new@test.com");
        req.setPassword("pass");
        req.setRole("MAKER");

        UserResponse result = authService.register(req);
        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getEmail()).isEqualTo("new@test.com");
    }

    @Test
    @DisplayName("Register fails when username already taken")
    void register_duplicateUsername_throws() {
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        RegisterRequest req = new RegisterRequest();
        req.setUsername("admin");
        req.setEmail("other@test.com");
        req.setPassword("pass");
        req.setRole("ADMIN");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("admin");
    }

    @Test
    @DisplayName("Register fails when email already registered")
    void register_duplicateEmail_throws() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("admin@test.com")).thenReturn(true);

        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("admin@test.com");
        req.setPassword("pass");
        req.setRole("ADMIN");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Register fails with unknown role")
    void register_unknownRole_throws() {
        when(userRepository.existsByUsername("u")).thenReturn(false);
        when(userRepository.existsByEmail("u@e.com")).thenReturn(false);
        when(roleRepository.findByName("SUPERUSER")).thenReturn(Optional.empty());

        RegisterRequest req = new RegisterRequest();
        req.setUsername("u");
        req.setEmail("u@e.com");
        req.setPassword("p");
        req.setRole("SUPERUSER");

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SUPERUSER");
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Login succeeds and returns access + refresh tokens")
    void login_success() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        when(userRepository.findByUsernameWithRoles("admin")).thenReturn(Optional.of(adminUser));
        when(jwtService.generateAccessToken(adminUser)).thenReturn("access-token");
        when(jwtProperties.getRefreshTtlHours()).thenReturn(24L);
        when(jwtProperties.getTtlMinutes()).thenReturn(30L);
        when(refreshTokenRepository.save(any())).thenReturn(RefreshToken.builder().build());

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Admin@123");

        TokenResponse result = authService.login(req);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getUsername()).isEqualTo("admin");
        verify(refreshTokenRepository).revokeAllByUser(adminUser);
    }

    @Test
    @DisplayName("Login throws BadCredentialsException on wrong password")
    void login_badCredentials_throws() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        doThrow(new BadCredentialsException("bad creds"))
                .when(authenticationManager).authenticate(any());

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Refresh with valid token returns new token pair")
    void refresh_validToken_succeeds() {
        RefreshToken stored = RefreshToken.builder()
                .tokenHash("any")
                .user(adminUser)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(jwtService.generateAccessToken(adminUser)).thenReturn("new-access-token");
        when(jwtProperties.getRefreshTtlHours()).thenReturn(24L);
        when(jwtProperties.getTtlMinutes()).thenReturn(30L);
        when(refreshTokenRepository.save(any())).thenReturn(RefreshToken.builder().build());

        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("some-uuid-refresh-token");

        TokenResponse result = authService.refresh(req);
        assertThat(result.getAccessToken()).isEqualTo("new-access-token");
        assertThat(stored.isRevoked()).isTrue(); // old token revoked
    }

    @Test
    @DisplayName("Refresh with invalid token hash throws BusinessException")
    void refresh_invalidToken_throws() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("bad-token");

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    @DisplayName("Refresh with revoked token throws BusinessException")
    void refresh_revokedToken_throws() {
        RefreshToken revoked = RefreshToken.builder()
                .tokenHash("h").user(adminUser)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true)
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("revoked-token");

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    @DisplayName("Refresh with expired token throws BusinessException")
    void refresh_expiredToken_throws() {
        RefreshToken expired = RefreshToken.builder()
                .tokenHash("h").user(adminUser)
                .expiresAt(Instant.now().minusSeconds(3600)) // in the past
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken("expired-token");

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }
}