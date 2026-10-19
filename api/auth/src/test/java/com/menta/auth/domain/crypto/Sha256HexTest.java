package com.menta.auth.domain.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class Sha256HexTest {

    @Test
    void hash_produces_the_known_sha256_digest_for_a_fixed_input() {
        // NIST test vector: SHA-256("abc").
        assertThat(Sha256Hex.hash("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hash_of_empty_string_matches_the_known_digest() {
        assertThat(Sha256Hex.hash(""))
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void hash_is_deterministic_and_lowercase() {
        String digest = Sha256Hex.hash("student@example.com");

        assertThat(digest).isEqualTo(Sha256Hex.hash("student@example.com"));
        assertThat(digest).isLowerCase();
        assertThat(digest).hasSize(64);
    }

    @Test
    void verify_algorithm_available_does_not_throw_on_a_real_jre() {
        assertThatCode(Sha256Hex::verifyAlgorithmAvailable).doesNotThrowAnyException();
    }
}
