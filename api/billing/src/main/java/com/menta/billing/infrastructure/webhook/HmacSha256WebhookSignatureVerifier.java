package com.menta.billing.infrastructure.webhook;

import com.menta.billing.application.dto.ParsedSignature;
import com.menta.billing.application.port.out.WebhookSignatureVerifier;
import com.menta.billing.domain.exception.WebhookSignatureInvalidException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Real HMAC-SHA256 verifier for Mercado Pago's {@code x-signature} header
 * (US-BILLING-002). The manifest is {@code id:{dataId};request-id:{requestId};ts:{timestamp};}
 * — Mercado Pago signs this constructed string, never the raw JSON body.
 *
 * <p>{@link MessageDigest#isEqual} is timing-safe by construction (constant
 * time regardless of where the first differing byte is) — {@code
 * String.equals}/{@code Arrays.equals} short-circuit on the first mismatch
 * and leak comparison length through response timing, exactly what this
 * check exists to prevent.</p>
 */
@Component
public class HmacSha256WebhookSignatureVerifier implements WebhookSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Override
    public ParsedSignature parse(String signatureHeader) {
        if (signatureHeader == null) {
            throw new WebhookSignatureInvalidException();
        }
        String timestamp = null;
        String hash = null;
        for (String field : signatureHeader.split(",")) {
            String[] keyValue = field.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            switch (keyValue[0].trim()) {
                case "ts" -> timestamp = keyValue[1].trim();
                case "v1" -> hash = keyValue[1].trim();
                default -> { }
            }
        }
        if (timestamp == null || hash == null) {
            throw new WebhookSignatureInvalidException();
        }
        return new ParsedSignature(timestamp, hash);
    }

    @Override
    public boolean isValid(String dataId, String requestId, String timestamp, String providedHash, String secret) {
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        byte[] computed = hmac(manifest, secret);
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(providedHash);
        } catch (IllegalArgumentException malformedHex) {
            return false;
        }
        return MessageDigest.isEqual(computed, provided);
    }

    private static byte[] hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
            throw new IllegalStateException("HmacSHA256 must always be available on the JVM", impossible);
        }
    }
}
