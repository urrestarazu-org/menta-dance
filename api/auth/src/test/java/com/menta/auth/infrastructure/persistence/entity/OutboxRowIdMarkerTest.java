package com.menta.auth.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OutboxRowIdMarkerTest {

    @Test
    void of_wraps_a_primitive_long_value() {
        OutboxRowIdMarker marker = OutboxRowIdMarker.of(42L);

        assertThat(marker.value()).isEqualTo(42L);
    }

    @Test
    void rejects_a_null_value() {
        assertThatThrownBy(() -> new OutboxRowIdMarker(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }

    @Test
    void toString_includes_the_wrapped_value() {
        assertThat(OutboxRowIdMarker.of(7L)).hasToString("OutboxRowIdMarker{7}");
    }

    @Test
    void equals_and_hashCode_are_value_based() {
        OutboxRowIdMarker first = OutboxRowIdMarker.of(1L);
        OutboxRowIdMarker second = OutboxRowIdMarker.of(1L);
        OutboxRowIdMarker different = OutboxRowIdMarker.of(2L);

        assertThat(first).isEqualTo(first);
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(different);
        assertThat(first).isNotEqualTo(null);
        assertThat(first).isNotEqualTo("not-a-marker");
    }
}
