package com.menta.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserIdTest {

    private static final UUID RAW = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void of_uuid_wraps_the_value() {
        assertThat(UserId.of(RAW).getValue()).isEqualTo(RAW);
    }

    @Test
    void of_uuid_rejects_null() {
        assertThatThrownBy(() -> UserId.of((UUID) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }

    @Test
    void of_string_parses_a_valid_uuid() {
        assertThat(UserId.of(RAW.toString()).getValue()).isEqualTo(RAW);
    }

    @Test
    void of_string_rejects_null_or_blank() {
        assertThatThrownBy(() -> UserId.of((String) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or empty");
        assertThatThrownBy(() -> UserId.of("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null or empty");
    }

    @Test
    void of_string_wraps_an_invalid_format_with_a_readable_message() {
        assertThatThrownBy(() -> UserId.of("not-a-uuid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid UserId format")
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_produces_a_fresh_random_id() {
        assertThat(UserId.generate().getValue()).isNotNull();
        assertThat(UserId.generate()).isNotEqualTo(UserId.generate());
    }

    @Test
    void equal_when_wrapping_the_same_uuid() {
        assertThat(UserId.of(RAW)).isEqualTo(UserId.of(RAW));
        assertThat(UserId.of(RAW)).hasSameHashCodeAs(UserId.of(RAW));
    }

    @Test
    void not_equal_to_null_or_a_different_type_or_a_different_uuid() {
        UserId id = UserId.of(RAW);

        assertThat(id).isEqualTo(id);
        assertThat(id).isNotEqualTo(null);
        assertThat(id).isNotEqualTo("not-a-user-id");
        assertThat(id).isNotEqualTo(UserId.generate());
    }

    @Test
    void to_string_renders_the_raw_uuid() {
        assertThat(UserId.of(RAW)).hasToString(RAW.toString());
    }
}
