package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentIdTest {

    @Test
    void of_uuid_wraps_the_value() {
        UUID uuid = UUID.randomUUID();
        assertThat(PaymentId.of(uuid).getValue()).isEqualTo(uuid);
    }

    @Test
    void of_uuid_rejects_null() {
        assertThatThrownBy(() -> PaymentId.of((UUID) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_string_parses_a_valid_uuid() {
        UUID uuid = UUID.randomUUID();
        assertThat(PaymentId.of(uuid.toString()).getValue()).isEqualTo(uuid);
    }

    @Test
    void of_string_rejects_null_or_blank() {
        assertThatThrownBy(() -> PaymentId.of((String) null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentId.of(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_string_rejects_malformed_uuid() {
        assertThatThrownBy(() -> PaymentId.of("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_produces_a_random_id() {
        assertThat(PaymentId.generate()).isNotEqualTo(PaymentId.generate());
    }

    @Test
    void equals_and_hashCode_are_value_based() {
        UUID uuid = UUID.randomUUID();
        assertThat(PaymentId.of(uuid)).isEqualTo(PaymentId.of(uuid)).hasSameHashCodeAs(PaymentId.of(uuid));
        assertThat(PaymentId.of(uuid)).isNotEqualTo(PaymentId.generate());
        assertThat(PaymentId.of(uuid)).isNotEqualTo("not-a-payment-id");
        assertThat(PaymentId.of(uuid)).isEqualTo(PaymentId.of(uuid));
    }

    @Test
    void toString_returns_the_uuid_string() {
        UUID uuid = UUID.randomUUID();
        assertThat(PaymentId.of(uuid)).hasToString(uuid.toString());
    }
}
