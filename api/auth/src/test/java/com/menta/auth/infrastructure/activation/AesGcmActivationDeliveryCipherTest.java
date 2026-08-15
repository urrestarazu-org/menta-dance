package com.menta.auth.infrastructure.activation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmActivationDeliveryCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encrypts_and_decrypts_without_retaining_the_plaintext() {
        AesGcmActivationDeliveryCipher cipher = new AesGcmActivationDeliveryCipher(KEY, 1);

        DeliveryEnvelope envelope = cipher.encrypt("student@example.com|raw-secret-token");

        assertThat(envelope.getNonce()).hasSize(12);
        assertThat(envelope.getCiphertext())
            .isNotEqualTo("student@example.com|raw-secret-token".getBytes());
        assertThat(cipher.decrypt(envelope)).isEqualTo("student@example.com|raw-secret-token");
    }

    @Test
    void creates_a_fresh_nonce_for_each_encryption() {
        AesGcmActivationDeliveryCipher cipher = new AesGcmActivationDeliveryCipher(KEY, 1);

        DeliveryEnvelope first = cipher.encrypt("payload");
        DeliveryEnvelope second = cipher.encrypt("payload");

        assertThat(first.getNonce()).isNotEqualTo(second.getNonce());
        assertThat(first.getCiphertext()).isNotEqualTo(second.getCiphertext());
    }

    @Test
    void rejects_a_key_that_is_not_256_bits() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesGcmActivationDeliveryCipher(shortKey, 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_an_envelope_from_an_unknown_key_version() {
        AesGcmActivationDeliveryCipher cipher = new AesGcmActivationDeliveryCipher(KEY, 1);
        DeliveryEnvelope differentVersion = DeliveryEnvelope.of(new byte[] {1}, new byte[12], 2);

        assertThatThrownBy(() -> cipher.decrypt(differentVersion))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
