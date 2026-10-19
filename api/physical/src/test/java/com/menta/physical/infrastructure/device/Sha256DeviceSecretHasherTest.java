package com.menta.physical.infrastructure.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Sha256DeviceSecretHasherTest {

    @Test
    void hasher_returns_the_lowercase_sha_256_digest() {
        Sha256DeviceSecretHasher hasher = new Sha256DeviceSecretHasher();

        assertThat(hasher.hash("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hasher_rejects_a_null_secret() {
        Sha256DeviceSecretHasher hasher = new Sha256DeviceSecretHasher();

        assertThatThrownBy(() -> hasher.hash(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
