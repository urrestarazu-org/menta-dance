package com.menta.billing.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.application.dto.ParsedSignature;
import com.menta.billing.domain.exception.WebhookSignatureInvalidException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class HmacSha256WebhookSignatureVerifierTest {

    private static final String SECRET = "test-secret";

    private final HmacSha256WebhookSignatureVerifier verifier = new HmacSha256WebhookSignatureVerifier();

    private static String hex(String manifest, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parse_splits_ts_and_v1() {
        ParsedSignature parsed = verifier.parse("ts=1700000000,v1=abcdef");

        assertThat(parsed.timestamp()).isEqualTo("1700000000");
        assertThat(parsed.hash()).isEqualTo("abcdef");
    }

    @Test
    void parse_tolerates_surrounding_whitespace_and_extra_fields() {
        ParsedSignature parsed = verifier.parse(" ts=1700000000 , v1=abcdef , extra=ignored");

        assertThat(parsed.timestamp()).isEqualTo("1700000000");
        assertThat(parsed.hash()).isEqualTo("abcdef");
    }

    @Test
    void parse_rejects_a_null_header() {
        assertThatThrownBy(() -> verifier.parse(null)).isInstanceOf(WebhookSignatureInvalidException.class);
    }

    @Test
    void parse_rejects_a_header_missing_ts_or_v1() {
        assertThatThrownBy(() -> verifier.parse("v1=abcdef")).isInstanceOf(WebhookSignatureInvalidException.class);
        assertThatThrownBy(() -> verifier.parse("ts=1700000000")).isInstanceOf(WebhookSignatureInvalidException.class);
    }

    @Test
    void isValid_true_for_a_correctly_computed_hmac() throws Exception {
        String manifest = "id:data-1;request-id:req-1;ts:1700000000;";
        String hash = hex(manifest, SECRET);

        assertThat(verifier.isValid("data-1", "req-1", "1700000000", hash, SECRET)).isTrue();
    }

    @Test
    void isValid_false_for_a_tampered_hash() throws Exception {
        String manifest = "id:data-1;request-id:req-1;ts:1700000000;";
        String hash = hex(manifest, SECRET);
        String tampered = hash.substring(0, hash.length() - 2) + "00";

        assertThat(verifier.isValid("data-1", "req-1", "1700000000", tampered, SECRET)).isFalse();
    }

    @Test
    void isValid_false_for_a_different_secret() throws Exception {
        String manifest = "id:data-1;request-id:req-1;ts:1700000000;";
        String hash = hex(manifest, "other-secret");

        assertThat(verifier.isValid("data-1", "req-1", "1700000000", hash, SECRET)).isFalse();
    }

    @Test
    void isValid_false_for_a_non_hex_hash() {
        assertThat(verifier.isValid("data-1", "req-1", "1700000000", "not-hex!!", SECRET)).isFalse();
    }
}
