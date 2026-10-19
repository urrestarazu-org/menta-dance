package com.menta.physical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionIdTest {

    private static final UUID RAW = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void of_uuid_rejects_null() {
        assertThatThrownBy(() -> SessionId.of((UUID) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_string_rejects_null_or_blank() {
        assertThatThrownBy(() -> SessionId.of((String) null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SessionId.of("  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_string_rejects_an_invalid_uuid_format() {
        assertThatThrownBy(() -> SessionId.of("not-a-uuid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid SessionId format");
    }

    @Test
    void of_string_parses_a_valid_uuid() {
        assertThat(SessionId.of(RAW.toString()).getValue()).isEqualTo(RAW);
    }

    @Test
    void generate_produces_a_random_id() {
        assertThat(SessionId.generate()).isNotEqualTo(SessionId.generate());
    }

    @Test
    void equal_when_the_underlying_uuid_matches() {
        SessionId a = SessionId.of(RAW);
        SessionId b = SessionId.of(RAW.toString());

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void not_equal_to_null_or_a_different_type() {
        SessionId a = SessionId.of(RAW);

        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("not-a-session-id");
    }

    @Test
    void to_string_is_the_raw_uuid() {
        assertThat(SessionId.of(RAW).toString()).isEqualTo(RAW.toString());
    }
}
