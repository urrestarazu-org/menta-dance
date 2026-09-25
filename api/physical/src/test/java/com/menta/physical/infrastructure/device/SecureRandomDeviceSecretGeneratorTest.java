package com.menta.physical.infrastructure.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecureRandomDeviceSecretGeneratorTest {

    @Test
    void generator_emits_a_32_byte_base64url_secret_without_padding() {
        SecureRandomDeviceSecretGenerator generator = new SecureRandomDeviceSecretGenerator();

        String secret = generator.generate();

        assertThat(secret).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(secret)).hasSize(32);
        assertThat(generator).doesNotHaveToString(secret);
    }

    @Test
    void generator_does_not_repeat_secrets_across_independent_calls() {
        SecureRandomDeviceSecretGenerator generator = new SecureRandomDeviceSecretGenerator();

        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }
}
