package com.menta.auth.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DeliveryEnvelopeTest {

    private static final byte[] CIPHERTEXT = {1, 2, 3};
    private static final byte[] VALID_NONCE = new byte[12];

    @Test
    void holds_ciphertext_nonce_and_key_version() {
        DeliveryEnvelope envelope = DeliveryEnvelope.of(CIPHERTEXT, VALID_NONCE, 1);

        assertThat(envelope.getCiphertext()).isEqualTo(CIPHERTEXT);
        assertThat(envelope.getNonce()).isEqualTo(VALID_NONCE);
        assertThat(envelope.getKeyVersion()).isEqualTo(1);
    }

    @Test
    void rejects_nonce_not_matching_aes_gcm_length() {
        assertThatThrownBy(() -> DeliveryEnvelope.of(CIPHERTEXT, new byte[8], 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("12 bytes");
    }

    @Test
    void rejects_non_positive_key_version() {
        assertThatThrownBy(() -> DeliveryEnvelope.of(CIPHERTEXT, VALID_NONCE, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("keyVersion");
    }

    @Test
    void rejects_empty_ciphertext() {
        assertThatThrownBy(() -> DeliveryEnvelope.of(new byte[0], VALID_NONCE, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ciphertext");
    }

    @Test
    void equal_when_every_field_matches() {
        DeliveryEnvelope a = DeliveryEnvelope.of(CIPHERTEXT, VALID_NONCE, 1);
        DeliveryEnvelope b = DeliveryEnvelope.of(CIPHERTEXT.clone(), VALID_NONCE.clone(), 1);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void not_equal_to_null_a_different_type_or_a_different_field() {
        DeliveryEnvelope envelope = DeliveryEnvelope.of(CIPHERTEXT, VALID_NONCE, 1);

        assertThat(envelope).isEqualTo(envelope);
        assertThat(envelope).isNotEqualTo(null);
        assertThat(envelope).isNotEqualTo("not-an-envelope");
        assertThat(envelope).isNotEqualTo(DeliveryEnvelope.of(new byte[] {9, 9, 9}, VALID_NONCE, 1));
        assertThat(envelope).isNotEqualTo(DeliveryEnvelope.of(CIPHERTEXT, VALID_NONCE, 2));
        byte[] otherNonce = new byte[12];
        otherNonce[0] = 9;
        assertThat(envelope).isNotEqualTo(DeliveryEnvelope.of(CIPHERTEXT, otherNonce, 1));
    }
}
