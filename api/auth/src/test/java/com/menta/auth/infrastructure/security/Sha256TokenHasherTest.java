package com.menta.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for Sha256TokenHasher (port/infrastructure adapter for
 * TokenHasher).
 *
 * The hashing contract: SHA-256 over the raw refresh UUID, lowercase hex,
 * 64 characters. The implementation is deterministic — same input yields
 * same digest (used for both insert + lookup paths in MySQL).
 */
class Sha256TokenHasherTest {

    private final Sha256TokenHasher hasher = new Sha256TokenHasher();

    @Test
    void hash_returns_64_char_lowercase_hex() {
        String hex = hasher.hash("some-uuid-input");

        assertThat(hex)
            .hasSize(64)
            .matches("[0-9a-f]{64}");
    }

    @Test
    void hash_is_deterministic_for_same_input() {
        String first = hasher.hash("repeatable-input");
        String second = hasher.hash("repeatable-input");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void hash_differs_for_different_inputs() {
        String h1 = hasher.hash("input-1");
        String h2 = hasher.hash("input-2");

        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void hash_matches_known_sha256_of_empty_string_reference() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String hex = hasher.hash("");

        assertThat(hex)
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void hash_rejects_null_input() {
        assertThatThrownBy(() -> hasher.hash((String) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null");
    }
}
