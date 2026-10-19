package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrialGrantTest {

    private static final Instant AT = Instant.parse("2026-08-18T10:00:00Z");
    private static final UUID BY = UUID.randomUUID();

    @Test
    void rejects_a_null_at() {
        assertThatThrownBy(() -> new TrialGrant(null, BY, "evaluación de producto", 7))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_a_null_by() {
        assertThatThrownBy(() -> new TrialGrant(AT, null, "evaluación de producto", 7))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_a_blank_reason() {
        assertThatThrownBy(() -> new TrialGrant(AT, BY, " ", 7))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_an_absent_reason() {
        assertThatThrownBy(() -> new TrialGrant(AT, BY, null, 7))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_zero_or_negative_days() {
        assertThatThrownBy(() -> new TrialGrant(AT, BY, "evaluación de producto", 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrialGrant(AT, BY, "evaluación de producto", -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accepts_valid_values_and_exposes_them() {
        TrialGrant grant = new TrialGrant(AT, BY, "evaluación de producto", 14);

        assertThat(grant.at()).isEqualTo(AT);
        assertThat(grant.by()).isEqualTo(BY);
        assertThat(grant.reason()).isEqualTo("evaluación de producto");
        assertThat(grant.days()).isEqualTo(14);
    }
}
