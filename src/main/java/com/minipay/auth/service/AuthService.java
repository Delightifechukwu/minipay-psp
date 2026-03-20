package com.minipay.auth.service;

import com.minipay.auth.domain.*;
import com.minipay.auth.dto.AuthDtos.*;
import com.minipay.auth.repo.*;
import com.minipay.common.errors.*;
import com.minipay.config.JwtProperties;
import io.github.bucket4j.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;

    // In-memory rate limiter per username (5 attempts / 60 seconds)
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }

        Role role = roleRepository.findByName(request.getRole().toUpperCase())
                .orElseThrow(() -> new BusinessException("Unknown role: " + request.getRole()));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status("ACTIVE")
                .roles(new HashSet<>(Set.of(role)))
                .build();

        user = userRepository.save(user);
        log.info("Registered new user: {} with role: {}", user.getUsername(), role.getName());
        return toUserResponse(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        checkLoginRateLimit(request.getUsername());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword()));
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for username: {}", request.getUsername());
            throw e;
        }

        User user = userRepository.findByUsernameWithRoles(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Revoke existing refresh tokens
        refreshTokenRepository.revokeAllByUser(user);

        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = UUID.randomUUID().toString();
        String refreshHash = sha256(rawRefresh);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(refreshHash)
                .user(user)
                .expiresAt(Instant.now().plus(Duration.ofHours(jwtProperties.getRefreshTtlHours())))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new TokenResponse(
                accessToken,
                rawRefresh,
                jwtProperties.getTtlMinutes() * 60,
                user.getUsername()
        );
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String hash = sha256(request.getRefreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException("Invalid refresh token"));

        if (stored.isRevoked()) {
            throw new BusinessException("Refresh token has been revoked");
        }
        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new BusinessException("Refresh token has expired");
        }

        // Rotate: revoke old, issue new
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = UUID.randomUUID().toString();

        RefreshToken newToken = RefreshToken.builder()
                .tokenHash(sha256(rawRefresh))
                .user(user)
                .expiresAt(Instant.now().plus(Duration.ofHours(jwtProperties.getRefreshTtlHours())))
                .build();
        refreshTokenRepository.save(newToken);

        return new TokenResponse(
                accessToken,
                rawRefresh,
                jwtProperties.getTtlMinutes() * 60,
                user.getUsername()
        );
    }

    private void checkLoginRateLimit(String username) {
        Bucket bucket = loginBuckets.computeIfAbsent(username, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(5)
                                .refillIntervally(5, Duration.ofSeconds(60))
                                .build())
                        .build()
        );
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfter = probe.getNanosToWaitForRefill() / 1_000_000_000;
            throw new RateLimitExceededException(
                    "Too many login attempts. Try again later.", retryAfter);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private UserResponse toUserResponse(User user) {
        UserResponse resp = new UserResponse();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setEmail(user.getEmail());
        resp.setStatus(user.getStatus());
        resp.setCreatedAt(user.getCreatedAt());
        resp.setRoles(user.getRoles().stream().map(Role::getName).toList());
        return resp;
    }
}
