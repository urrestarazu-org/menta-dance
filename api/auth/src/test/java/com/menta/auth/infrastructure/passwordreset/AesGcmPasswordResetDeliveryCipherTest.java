package com.menta.auth.infrastructure.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Mirrors the activation cipher's contract for the password-reset key. */
class AesGcmPasswordResetDeliveryCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY =
        Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String PLAINTEXT = "student@example.com|raw-reset-token";

    private final AesGcmPasswordResetDeliveryCipher cipher =
        new AesGcmPasswordResetDeliveryCipher(KEY, 1);

    @Nested
    @DisplayName("Round-trip")
    class RoundTrip {

        @Test
        void decrypts_what_it_encrypted() {
            assertThat(cipher.decrypt(cipher.encrypt(PLAINTEXT))).isEqualTo(PLAINTEXT);
        }

        @Test
        void never_yields_the_same_ciphertext_twice() {
            // A fresh nonce per encryption is mandatory: reusing one under the
            // same key breaks GCM outright.
            DeliveryEnvelope first = cipher.encrypt(PLAINTEXT);
            DeliveryEnvelope second = cipher.encrypt(PLAINTEXT);

            assertThat(first.getCiphertext()).isNotEqualTo(second.getCiphertext());
            assertThat(first.getNonce()).isNotEqualTo(second.getNonce());
        }

        @Test
        void the_ciphertext_never_contains_the_raw_token() {
            DeliveryEnvelope envelope = cipher.encrypt(PLAINTEXT);

            assertThat(new String(envelope.getCiphertext())).doesNotContain("raw-reset-token");
        }
    }

    @Nested
    @DisplayName("Aislamiento de la clave de activación")
    class KeyIsolation {

        @Test
        void a_payload_encrypted_with_another_key_cannot_be_decrypted() {
            // The whole point of a separate key: an activation envelope must be
            // undecryptable here, so compromising one flow's key does not leak
            // the other's material.
            AesGcmPasswordResetDeliveryCipher otherCipher =
                new AesGcmPasswordResetDeliveryCipher(OTHER_KEY, 1);
            DeliveryEnvelope foreign = otherCipher.encrypt(PLAINTEXT);

            assertThatThrownBy(() -> cipher.decrypt(foreign))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejects_an_envelope_from_a_different_key_version() {
            DeliveryEnvelope wrongVersion =
                DeliveryEnvelope.of(new byte[16], new byte[12], 2);

            assertThatThrownBy(() -> cipher.decrypt(wrongVersion))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Validación de configuración")
    class Configuration {

        @Test
        void rejects_a_key_that_is_not_256_bits() {
            String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

            assertThatThrownBy(() -> new AesGcmPasswordResetDeliveryCipher(shortKey, 1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejects_a_blank_key() {
            assertThatThrownBy(() -> new AesGcmPasswordResetDeliveryCipher("  ", 1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejects_a_non_positive_key_version() {
            assertThatThrownBy(() -> new AesGcmPasswordResetDeliveryCipher(KEY, 0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejects_blank_plaintext() {
            assertThatThrownBy(() -> cipher.encrypt("  "))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
