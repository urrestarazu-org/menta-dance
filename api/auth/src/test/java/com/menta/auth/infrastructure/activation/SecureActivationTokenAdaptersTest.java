package com.menta.auth.infrastructure.activation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecureActivationTokenAdaptersTest {

    @Test
    void generator_emits_a_32_byte_base64url_token_without_padding() {
        SecureRandomActivationTokenGenerator generator = new SecureRandomActivationTokenGenerator();

        String token = generator.generate();

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
        assertThat(generator).doesNotHaveToString(token);
    }

    @Test
    void generator_does_not_repeat_tokens_across_independent_requests() {
        SecureRandomActivationTokenGenerator generator = new SecureRandomActivationTokenGenerator();

        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }

    @Test
    void hasher_returns_the_lowercase_sha_256_digest() {
        Sha256ActivationTokenHasher hasher = new Sha256ActivationTokenHasher();

        assertThat(hasher.hash("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hasher_rejects_a_null_token() {
        Sha256ActivationTokenHasher hasher = new Sha256ActivationTokenHasher();

        assertThatThrownBy(() -> hasher.hash(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
