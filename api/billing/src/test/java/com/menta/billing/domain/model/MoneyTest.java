package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void accepts_a_positive_amount_with_a_valid_iso_currency() {
        Money money = Money.of(new BigDecimal("15000.00"), "ARS");

        assertThat(money.getAmount()).isEqualByComparingTo("15000.00");
        assertThat(money.getCurrency()).isEqualTo("ARS");
    }

    @Test
    void accepts_zero_as_a_free_tier_price() {
        Money money = Money.of(BigDecimal.ZERO, "ARS");

        assertThat(money.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejects_a_negative_amount() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-1"), "ARS"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_null_amount() {
        assertThatThrownBy(() -> Money.of(null, "ARS"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_currency_that_is_not_a_three_letter_iso_code() {
        assertThatThrownBy(() -> Money.of(BigDecimal.TEN, "pesos"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.TEN, "AR"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_null_currency() {
        assertThatThrownBy(() -> Money.of(BigDecimal.TEN, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void two_amounts_with_different_scale_but_same_value_are_equal() {
        Money a = Money.of(new BigDecimal("10.0"), "ARS");
        Money b = Money.of(new BigDecimal("10.00"), "ARS");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void same_amount_different_currency_are_not_equal() {
        Money a = Money.of(BigDecimal.TEN, "ARS");
        Money b = Money.of(BigDecimal.TEN, "USD");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void is_equal_to_itself() {
        Money money = Money.of(BigDecimal.TEN, "ARS");

        assertThat(money).isEqualTo(money);
    }

    @Test
    void is_not_equal_to_null_or_a_different_type() {
        Money money = Money.of(BigDecimal.TEN, "ARS");

        assertThat(money).isNotEqualTo(null);
        assertThat(money).isNotEqualTo("10 ARS");
    }

    @Test
    void toString_includes_amount_and_currency() {
        Money money = Money.of(BigDecimal.TEN, "ARS");

        assertThat(money.toString()).contains("10").contains("ARS");
    }
}
