package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanIdTest {

    @Test
    void wraps_a_uuid() {
        UUID uuid = UUID.randomUUID();

        PlanId id = PlanId.of(uuid);

        assertThat(id.getValue()).isEqualTo(uuid);
    }

    @Test
    void parses_a_valid_uuid_string() {
        UUID uuid = UUID.randomUUID();

        PlanId id = PlanId.of(uuid.toString());

        assertThat(id.getValue()).isEqualTo(uuid);
    }

    @Test
    void rejects_a_null_uuid() {
        assertThatThrownBy(() -> PlanId.of((UUID) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_blank_string() {
        assertThatThrownBy(() -> PlanId.of("  "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_malformed_string() {
        assertThatThrownBy(() -> PlanId.of("not-a-uuid"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_produces_distinct_ids() {
        assertThat(PlanId.generate()).isNotEqualTo(PlanId.generate());
    }

    @Test
    void equal_ids_share_hash_code_and_are_equal() {
        UUID uuid = UUID.randomUUID();

        assertThat(PlanId.of(uuid)).isEqualTo(PlanId.of(uuid));
        assertThat(PlanId.of(uuid).hashCode()).isEqualTo(PlanId.of(uuid).hashCode());
        assertThat(PlanId.of(uuid).toString()).isEqualTo(uuid.toString());
    }

    @Test
    void is_equal_to_itself() {
        PlanId id = PlanId.generate();

        assertThat(id).isEqualTo(id);
    }

    @Test
    void is_not_equal_to_null_or_a_different_type() {
        PlanId id = PlanId.generate();

        assertThat(id).isNotEqualTo(null);
        assertThat(id).isNotEqualTo(id.getValue());
    }
}
