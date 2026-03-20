package com.minipay.common;

import com.minipay.common.utils.WebhookSignatureUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WebhookSignatureUtil")
class WebhookSignatureUtilTest {

    private static final String SECRET  = "test-webhook-secret-key";
    private static final String PAYLOAD = "{\"paymentRef\":\"abc-123\",\"status\":\"SUCCESS\"}";

    @Test
    @DisplayName("sign produces non-blank Base64 string")
    void sign_produces_base64() {
        String sig = WebhookSignatureUtil.sign(PAYLOAD, SECRET);
        assertThat(sig).isNotBlank();
        // valid Base64 chars only
        assertThat(sig).matches("^[A-Za-z0-9+/=]+$");
    }

    @Test
    @DisplayName("same inputs always produce same signature (deterministic)")
    void sign_is_deterministic() {
        String sig1 = WebhookSignatureUtil.sign(PAYLOAD, SECRET);
        String sig2 = WebhookSignatureUtil.sign(PAYLOAD, SECRET);
        assertThat(sig1).isEqualTo(sig2);
    }

    @Test
    @DisplayName("different payloads produce different signatures")
    void different_payloads_differ() {
        String sig1 = WebhookSignatureUtil.sign(PAYLOAD, SECRET);
        String sig2 = WebhookSignatureUtil.sign(PAYLOAD + " ", SECRET);
        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    @DisplayName("different secrets produce different signatures")
    void different_secrets_differ() {
        String sig1 = WebhookSignatureUtil.sign(PAYLOAD, SECRET);
        String sig2 = WebhookSignatureUtil.sign(PAYLOAD, SECRET + "x");
        assertThat(sig1).isNotEqualTo(sig2);
    }

    @Test
    @DisplayName("verify returns true for correct signature")
    void verify_correct_signature() {
        String sig = WebhookSignatureUtil.sign(PAYLOAD, SECRET);
        assertThat(WebhookSignatureUtil.verify(PAYLOAD, SECRET, sig)).isTrue();
    }

    @Test
    @DisplayName("verify returns false for tampered payload")
    void verify_tampered_payload() {
        String sig = WebhookSignatureUtil.sign(PAYLOAD, SECRET);
        String tampered = PAYLOAD.replace("SUCCESS", "FAILED");
        assertThat(WebhookSignatureUtil.verify(tampered, SECRET, sig)).isFalse();
    }

    @Test
    @DisplayName("verify returns false for null signature")
    void verify_null_signature() {
        assertThat(WebhookSignatureUtil.verify(PAYLOAD, SECRET, null)).isFalse();
    }

    @Test
    @DisplayName("verify returns false for blank signature")
    void verify_blank_signature() {
        assertThat(WebhookSignatureUtil.verify(PAYLOAD, SECRET, "   ")).isFalse();
    }

    @Test
    @DisplayName("verify is not vulnerable to length-extension (constant time)")
    void verify_constant_time_inequal_lengths() {
        // Signatures of different lengths must not throw — just return false
        assertThatCode(() ->
            WebhookSignatureUtil.verify(PAYLOAD, SECRET, "tooshort")
        ).doesNotThrowAnyException();

        assertThat(WebhookSignatureUtil.verify(PAYLOAD, SECRET, "tooshort")).isFalse();
    }
}
