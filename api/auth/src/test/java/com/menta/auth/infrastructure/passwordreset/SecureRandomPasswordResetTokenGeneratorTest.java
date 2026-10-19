package com.menta.auth.infrastructure.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SecureRandomPasswordResetTokenGeneratorTest {

    private final SecureRandomPasswordResetTokenGenerator generator =
        new SecureRandomPasswordResetTokenGenerator();

    @Test
    void generates_a_url_safe_unpadded_base64_encoding_of_32_bytes() {
        String token = generator.generate();

        assertThat(token).doesNotContain("+", "/", "=");
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertThat(decoded).hasSize(32);
    }

    @Test
    void consecutive_generations_differ() {
        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void generates_unique_values_across_many_calls() {
        Set<String> generated = new HashSet<>();
        IntStream.range(0, 100).forEach(i -> generated.add(generator.generate()));

        assertThat(generated).hasSize(100);
    }

    @Test
    void rejects_a_null_secureRandom() {
        assertThatThrownBy(() -> new SecureRandomPasswordResetTokenGenerator(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }
}
