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
}
