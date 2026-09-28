package com.menta.billing.infrastructure.proof;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.menta.billing.domain.model.PaymentId;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signs a time-limited payment-proof access token (#33, US-BILLING-005, design D1/C2/C3/C6).
 *
 * <p>{@code token = b64url(payload) + "." + b64url(hmac(payload))}, {@code payload =
 * "1:{paymentId}:{expiresAtEpochSecond}"}. The real HMAC mechanics are lifted from {@link
 * com.menta.billing.infrastructure.webhook.HmacSha256WebhookSignatureVerifier} (design D1), never
 * from the QR module's non-HMAC placeholder. {@link Base64.Encoder#withoutPadding()
 * getUrlEncoder().withoutPadding()} is deliberate: this token travels as a query parameter, so
 * {@code +}, {@code /} and {@code =} must never appear — no percent-encoding, no double-decode
 * ambiguity, no truncation at a stray {@code =}.</p>
 */
@Component
public class ProofAccessTokenSigner {

    static final String HMAC_ALGORITHM = "HmacSHA256";
    static final String VERSION = "1";

    private final String secret;
    private final Duration ttl;

    /**
     * Design C3/D6: {@code secret} has no usable default — an absent property is an unresolvable
     * Spring placeholder that fails application startup before this constructor ever runs; a
     * present-but-blank value is caught here and also fails startup. Both are fatal on purpose:
     * signing with an empty key would silently produce a forgeable token.
     */
    public ProofAccessTokenSigner(
        @Value("${billing.bank-transfer.proof.token-secret}") String secret,
        @Value("${billing.bank-transfer.proof.token-ttl-seconds:900}") long ttlSeconds
    ) {
        this.secret = requiredValue(secret, "payment proof token secret");
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /** {@code payload = "1:{paymentId}:{expiresAtEpochSecond}"} (design C2). */
    public String sign(PaymentId paymentId, Instant now) {
        String payload = VERSION + ":" + paymentId.getValue() + ":" + now.plus(ttl).getEpochSecond();
        return encode(payload.getBytes(UTF_8)) + "." + encode(ProofAccessTokenHmac.hmac(payload, secret));
    }

    private static String encode(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String requiredValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }

    /** Package-private so {@link ProofAccessTokenVerifier} recomputes with the exact same mechanics. */
    static final class ProofAccessTokenHmac {

        private ProofAccessTokenHmac() {
        }

        static byte[] hmac(String data, String secret) {
            try {
                Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                mac.init(new SecretKeySpec(secret.getBytes(UTF_8), HMAC_ALGORITHM));
                return mac.doFinal(data.getBytes(UTF_8));
            } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
                throw new IllegalStateException("HmacSHA256 must always be available on the JVM", impossible);
            }
        }
    }
}
