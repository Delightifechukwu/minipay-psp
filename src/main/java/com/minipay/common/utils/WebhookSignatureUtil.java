package com.minipay.common.utils;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility for computing and verifying HMAC-SHA256 webhook signatures.
 *
 * <p>Signature scheme:
 * <ol>
 *   <li>Canonical payload = compact JSON string (no extra whitespace)</li>
 *   <li>HMAC-SHA256(canonicalPayload, webhookSecret)</li>
 *   <li>Base64-encode the raw HMAC bytes</li>
 *   <li>Send as {@code X-Signature} header</li>
 * </ol>
 */
@Slf4j
public final class WebhookSignatureUtil {

    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSignatureUtil() {}

    /**
     * Computes the Base64-encoded HMAC-SHA256 of the given payload using the secret.
     */
    public static String sign(String canonicalPayload, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute webhook signature", e);
        }
    }

    /**
     * Verifies the provided signature against the expected signature.
     * Uses constant-time comparison to prevent timing attacks.
     */
    public static boolean verify(String canonicalPayload, String secret, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        String expected = sign(canonicalPayload, secret);
        return constantTimeEquals(expected, providedSignature);
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }
}
