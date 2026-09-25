package com.menta.physical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceIdTest {

    private static final UUID RAW = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void of_uuid_rejects_null() {
        assertThatThrownBy(() -> DeviceId.of((UUID) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_string_rejects_null_or_blank() {
        assertThatThrownBy(() -> DeviceId.of((String) null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeviceId.of("  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_string_rejects_an_invalid_uuid_format() {
        assertThatThrownBy(() -> DeviceId.of("not-a-uuid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid DeviceId format");
    }

    @Test
    void of_string_parses_a_valid_uuid() {
        assertThat(DeviceId.of(RAW.toString()).getValue()).isEqualTo(RAW);
    }

    @Test
    void generate_produces_a_random_id() {
        assertThat(DeviceId.generate()).isNotEqualTo(DeviceId.generate());
    }

    @Test
    void equal_when_the_underlying_uuid_matches() {
        DeviceId a = DeviceId.of(RAW);
        DeviceId b = DeviceId.of(RAW.toString());

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void not_equal_to_null_or_a_different_type() {
        DeviceId a = DeviceId.of(RAW);

        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("not-a-device-id");
    }

    @Test
    void to_string_is_the_raw_uuid() {
        assertThat(DeviceId.of(RAW).toString()).isEqualTo(RAW.toString());
    }
}
