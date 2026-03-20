package com.minipay.config;

import com.minipay.auth.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Runs once on startup to catch critical security misconfigurations before
 * the application accepts traffic.
 *
 * <ul>
 *   <li>In the {@code prod} profile, fails fast if JWT_SECRET is the insecure default.</li>
 *   <li>In all profiles, warns loudly if the admin account still uses the default password.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartupSecurityCheck implements ApplicationListener<ApplicationReadyEvent> {

    private static final String INSECURE_JWT_DEFAULT =
            "minipay-super-secret-key-change-in-production-must-be-256-bits";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@123";

    private final JwtProperties   jwtProperties;
    private final Environment     environment;
    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        checkJwtSecret();
        checkAdminPassword();
    }

    private void checkJwtSecret() {
        if (INSECURE_JWT_DEFAULT.equals(jwtProperties.getSecret())) {
            boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
            if (isProd) {
                throw new IllegalStateException(
                        "FATAL: JWT_SECRET is set to the insecure default value. " +
                        "Set the JWT_SECRET environment variable to a secure random 256-bit key " +
                        "before starting in production.");
            }
            log.warn("⚠ SECURITY: JWT_SECRET is using the insecure default. " +
                     "Set JWT_SECRET env var before deploying to production.");
        }
    }

    private void checkAdminPassword() {
        userRepository.findByUsernameWithRoles("admin").ifPresent(admin -> {
            if (passwordEncoder.matches(DEFAULT_ADMIN_PASSWORD, admin.getPassword())) {
                log.warn("⚠ SECURITY: Admin account is using the default password 'Admin@123'. " +
                         "Change it immediately via POST /api/v1/auth/register or direct DB update.");
            }
        });
    }
}