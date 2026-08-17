package com.menta.auth.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers the default {@link TokenHasher#hash(UUID)} overload only; the abstract method is a contract. */
class TokenHasherTest {

    private final TokenHasher hasher = rawRefreshToken -> "hashed:" + rawRefreshToken;

    @Test
    void uuid_overload_delegates_to_the_string_overload() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(hasher.hash(id)).isEqualTo("hashed:" + id);
    }

    @Test
    void uuid_overload_rejects_null() {
        assertThatThrownBy(() -> hasher.hash((UUID) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("refreshId");
    }
}
