package com.menta.billing.infrastructure.proof;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.menta.billing.domain.exception.InvalidProofAccessTokenException;
import com.menta.billing.domain.model.PaymentId;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies a payment-proof access token minted by {@link ProofAccessTokenSigner} (#33,
 * US-BILLING-005, design D1/C2/C6).
 *
 * <p>Validation order — shape, then signature, then payload, then expiry — is load-bearing
 * (design C2): the signature is checked <strong>before</strong> expiry, and every failure mode
 * (missing/extra separator, non-Base64 half, bad MAC, unknown version, malformed payload,
 * non-UUID id, expired) throws the exact same {@link InvalidProofAccessTokenException} with the
 * exact same message. A forged token therefore reveals nothing about expiry windows — the
 * response cannot distinguish "expired" from "never valid" (anti-oracle property).</p>
 */
@Component
public class ProofAccessTokenVerifier {

    private final String secret;

    public ProofAccessTokenVerifier(
        @Value("${billing.bank-transfer.proof.token-secret}") String secret
    ) {
        this.secret = requiredValue(secret, "payment proof token secret");
    }

    public PaymentId verify(String token, Instant now) {
        if (token == null) {
            throw new InvalidProofAccessTokenException();
        }
        String[] halves = token.split("\\.", -1);
        if (halves.length != 2) {
            throw new InvalidProofAccessTokenException();
        }
        byte[] payloadBytes = decode(halves[0]);
        byte[] providedMac = decode(halves[1]);

        byte[] expectedMac = ProofAccessTokenSigner.ProofAccessTokenHmac.hmac(
            new String(payloadBytes, UTF_8), secret
        );
        if (!MessageDigest.isEqual(expectedMac, providedMac)) {
            throw new InvalidProofAccessTokenException();
        }

        String[] fields = new String(payloadBytes, UTF_8).split(":", -1);
        if (fields.length != 3 || !ProofAccessTokenSigner.VERSION.equals(fields[0])) {
            throw new InvalidProofAccessTokenException();
        }
        PaymentId paymentId = parsePaymentId(fields[1]);
        long expiresAtEpochSecond = parseEpochSecond(fields[2]);
        if (now.getEpochSecond() > expiresAtEpochSecond) {
            throw new InvalidProofAccessTokenException();
        }
        return paymentId;
    }

    private static byte[] decode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException malformedBase64) {
            throw new InvalidProofAccessTokenException();
        }
    }

    private static PaymentId parsePaymentId(String value) {
        try {
            return PaymentId.of(value);
        } catch (IllegalArgumentException malformedId) {
            throw new InvalidProofAccessTokenException();
        }
    }

    private static long parseEpochSecond(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException malformedEpoch) {
            throw new InvalidProofAccessTokenException();
        }
    }

    private static String requiredValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
