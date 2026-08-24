package com.menta.physical.infrastructure.qr;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link FormatQrCredentialSignatureService}. The
 * implementation is a placeholder, but the token shape is the contract
 * {@link com.menta.physical.application.usecase.QrCredentialParser} and a
 * future HMAC-backed implementation must keep — verify it here so any
 * refactor breaks this test loudly.
 */
class FormatQrCredentialSignatureServiceTest {

    private final FormatQrCredentialSignatureService service = new FormatQrCredentialSignatureService();

    @Test
    void formats_the_four_claims_as_colon_separated_segments_with_the_qr_prefix() {
        String token = service.sign("student-1", "session-1", "jti-1", 1_700_000_000L);

        assertThat(token).isEqualTo("qr:student-1:session-1:jti-1:1700000000");
    }

    @Test
    void is_deterministic_for_the_same_inputs() {
        // The whole point of a placeholder "signature" a verifier can
        // recompute: same inputs must always produce the exact same string.
        String first = service.sign("student-1", "session-1", "jti-1", 1_700_000_000L);
        String second = service.sign("student-1", "session-1", "jti-1", 1_700_000_000L);

        assertThat(first).isEqualTo(second);
    }
}
