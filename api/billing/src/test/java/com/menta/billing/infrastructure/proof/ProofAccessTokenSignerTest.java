package com.menta.billing.infrastructure.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.domain.model.PaymentId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * Unit + startup-failure coverage for {@link ProofAccessTokenSigner} (#33, US-BILLING-005, design
 * D1/C2/C3/C6). No Spring context for the round-trip cases — the secret/TTL are injected directly,
 * mirroring {@code HmacSha256WebhookSignatureVerifierTest}'s no-context style; a minimal context is
 * used only to prove D6's fail-fast property against real Spring placeholder resolution.
 */
class ProofAccessTokenSignerTest {

    private static final String SECRET = "test-only-proof-token-secret-32-bytes-min";
    private static final Instant FIXED_NOW = Instant.parse("2026-09-28T12:00:00Z");

    @Test
    void sign_produces_a_token_shaped_as_two_url_safe_base64_halves_joined_by_a_dot() {
        ProofAccessTokenSigner signer = new ProofAccessTokenSigner(SECRET, 900);

        String token = signer.sign(PaymentId.generate(), FIXED_NOW);

        assertThat(token).matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
    }

    @Test
    void sign_then_verify_round_trips_within_the_ttl() {
        ProofAccessTokenSigner signer = new ProofAccessTokenSigner(SECRET, 900);
        ProofAccessTokenVerifier verifier = new ProofAccessTokenVerifier(SECRET);
        PaymentId paymentId = PaymentId.generate();

        String token = signer.sign(paymentId, FIXED_NOW);
        PaymentId verified = verifier.verify(token, FIXED_NOW.plusSeconds(600));

        assertThat(verified).isEqualTo(paymentId);
    }

    @Test
    void verify_at_exactly_one_second_before_expiry_is_valid() {
        ProofAccessTokenSigner signer = new ProofAccessTokenSigner(SECRET, 900);
        ProofAccessTokenVerifier verifier = new ProofAccessTokenVerifier(SECRET);
        PaymentId paymentId = PaymentId.generate();

        String token = signer.sign(paymentId, FIXED_NOW);
        PaymentId verified = verifier.verify(token, FIXED_NOW.plusSeconds(899));

        assertThat(verified).isEqualTo(paymentId);
    }

    @Test
    void verify_one_second_past_expiry_is_rejected() {
        ProofAccessTokenSigner signer = new ProofAccessTokenSigner(SECRET, 900);
        ProofAccessTokenVerifier verifier = new ProofAccessTokenVerifier(SECRET);
        String token = signer.sign(PaymentId.generate(), FIXED_NOW);

        assertThatThrownBy(() -> verifier.verify(token, FIXED_NOW.plusSeconds(901)))
            .isInstanceOf(com.menta.billing.domain.exception.InvalidProofAccessTokenException.class);
    }

    @Test
    void verify_rejects_a_tampered_payload_with_the_same_exception_as_expiry() {
        ProofAccessTokenSigner signer = new ProofAccessTokenSigner(SECRET, 900);
        ProofAccessTokenVerifier verifier = new ProofAccessTokenVerifier(SECRET);
        String token = signer.sign(PaymentId.generate(), FIXED_NOW);
        String[] halves = token.split("\\.");
        String tampered = halves[0] + "X." + halves[1];

        assertThatThrownBy(() -> verifier.verify(tampered, FIXED_NOW.plusSeconds(1)))
            .isInstanceOf(com.menta.billing.domain.exception.InvalidProofAccessTokenException.class);
    }

    @Test
    void verify_rejects_a_tampered_mac() {
        ProofAccessTokenSigner signer = new ProofAccessTokenSigner(SECRET, 900);
        ProofAccessTokenVerifier verifier = new ProofAccessTokenVerifier(SECRET);
        String token = signer.sign(PaymentId.generate(), FIXED_NOW);
        String[] halves = token.split("\\.");
        String tampered = halves[0] + "." + halves[1] + "X";

        assertThatThrownBy(() -> verifier.verify(tampered, FIXED_NOW.plusSeconds(1)))
            .isInstanceOf(com.menta.billing.domain.exception.InvalidProofAccessTokenException.class);
    }

    @Test
    void verify_rejects_swapped_halves() {
        ProofAccessTokenSigner signer = new ProofAccessTokenSigner(SECRET, 900);
        ProofAccessTokenVerifier verifier = new ProofAccessTokenVerifier(SECRET);
        String token = signer.sign(PaymentId.generate(), FIXED_NOW);
        String[] halves = token.split("\\.");
        String swapped = halves[1] + "." + halves[0];

        assertThatThrownBy(() -> verifier.verify(swapped, FIXED_NOW.plusSeconds(1)))
            .isInstanceOf(com.menta.billing.domain.exception.InvalidProofAccessTokenException.class);
    }

    @Test
    void verify_rejects_a_missing_dot_separator() {
        ProofAccessTokenVerifier verifier = new ProofAccessTokenVerifier(SECRET);

        assertThatThrownBy(() -> verifier.verify("nodothere", FIXED_NOW))
            .isInstanceOf(com.menta.billing.domain.exception.InvalidProofAccessTokenException.class);
    }

    @Test
    void verify_rejects_an_extra_dot_separator() {
        ProofAccessTokenVerifier verifier = new ProofAccessTokenVerifier(SECRET);

        assertThatThrownBy(() -> verifier.verify("a.b.c", FIXED_NOW))
            .isInstanceOf(com.menta.billing.domain.exception.InvalidProofAccessTokenException.class);
    }

    @Test
    void verify_rejects_a_non_base64_half() {
        ProofAccessTokenVerifier verifier = new ProofAccessTokenVerifier(SECRET);

        assertThatThrownBy(() -> verifier.verify("not!base64.also!not", FIXED_NOW))
            .isInstanceOf(com.menta.billing.domain.exception.InvalidProofAccessTokenException.class);
    }

    @Test
    void verify_rejects_an_unknown_version() {
        ProofAccessTokenVerifier verifier = new ProofAccessTokenVerifier(SECRET);
        String payload = "2:" + PaymentId.generate().getValue() + ":" + FIXED_NOW.plusSeconds(900).getEpochSecond();
        String token = encode(payload) + "." + encodeBytes(macFor(payload));

        assertThatThrownBy(() -> verifier.verify(token, FIXED_NOW))
            .isInstanceOf(com.menta.billing.domain.exception.InvalidProofAccessTokenException.class);
    }

    @Test
    void verify_rejects_a_non_uuid_payment_id() {
        ProofAccessTokenVerifier verifier = new ProofAccessTokenVerifier(SECRET);
        String payload = "1:not-a-uuid:" + FIXED_NOW.plusSeconds(900).getEpochSecond();
        String token = encode(payload) + "." + encodeBytes(macFor(payload));

        assertThatThrownBy(() -> verifier.verify(token, FIXED_NOW))
            .isInstanceOf(com.menta.billing.domain.exception.InvalidProofAccessTokenException.class);
    }

    @Test
    void verify_rejects_a_token_signed_with_a_different_secret() {
        ProofAccessTokenSigner otherSigner = new ProofAccessTokenSigner("a-completely-different-secret-value", 900);
        ProofAccessTokenVerifier verifier = new ProofAccessTokenVerifier(SECRET);
        String token = otherSigner.sign(PaymentId.generate(), FIXED_NOW);

        assertThatThrownBy(() -> verifier.verify(token, FIXED_NOW.plusSeconds(1)))
            .isInstanceOf(com.menta.billing.domain.exception.InvalidProofAccessTokenException.class);
    }

    // --- D6/C3: fail-fast secret/TTL wiring ---

    @Test
    void a_blank_secret_fails_construction_immediately() {
        assertThatThrownBy(() -> new ProofAccessTokenSigner("   ", 900))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_null_secret_fails_construction_immediately() {
        assertThatThrownBy(() -> new ProofAccessTokenSigner(null, 900))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** D6: an absent property is an unresolvable Spring placeholder — the context itself fails to start. */
    @Test
    void an_absent_secret_property_fails_spring_context_startup() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        MutablePropertySources sources = context.getEnvironment().getPropertySources();
        sources.addFirst(new MapPropertySource("test", java.util.Map.of()));
        context.register(PlaceholderConfig.class, SignerOnlyConfig.class);

        assertThatThrownBy(context::refresh)
            .isInstanceOf(org.springframework.beans.factory.BeanCreationException.class);

        context.close();
    }

    private static String encode(String raw) {
        return encodeBytes(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String encodeBytes(byte[] raw) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static byte[] macFor(String payload) {
        return ProofAccessTokenSigner.ProofAccessTokenHmac.hmac(payload, SECRET);
    }

    @Configuration
    static class PlaceholderConfig {
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }

    @Configuration
    static class SignerOnlyConfig {
        @Bean
        ProofAccessTokenSigner signer(
            @org.springframework.beans.factory.annotation.Value("${billing.bank-transfer.proof.token-secret}")
                String secret
        ) {
            return new ProofAccessTokenSigner(secret, 900);
        }
    }
}
