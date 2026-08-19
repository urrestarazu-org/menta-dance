package com.menta.virtual.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModuleIdTest {

    private static final UUID RAW = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void of_uuid_rejects_null() {
        assertThatThrownBy(() -> ModuleId.of((UUID) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_string_rejects_null_or_blank() {
        assertThatThrownBy(() -> ModuleId.of((String) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ModuleId.of("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_string_rejects_an_invalid_uuid_format() {
        assertThatThrownBy(() -> ModuleId.of("not-a-uuid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid ModuleId format");
    }

    @Test
    void of_string_parses_a_valid_uuid() {
        assertThat(ModuleId.of(RAW.toString()).getValue()).isEqualTo(RAW);
    }

    @Test
    void generate_produces_a_random_id() {
        assertThat(ModuleId.generate()).isNotEqualTo(ModuleId.generate());
    }

    @Test
    void equal_when_the_underlying_uuid_matches() {
        ModuleId a = ModuleId.of(RAW);
        ModuleId b = ModuleId.of(RAW.toString());

        assertThat(a).isEqualTo(a);
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void not_equal_to_null_or_a_different_type() {
        ModuleId a = ModuleId.of(RAW);

        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("not-a-module-id");
    }

    @Test
    void to_string_is_the_raw_uuid() {
        assertThat(ModuleId.of(RAW).toString()).isEqualTo(RAW.toString());
    }
}
