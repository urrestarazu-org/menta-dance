package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PaymentTargetTest {

    @Test
    void physical_exposes_the_quote_id() {
        assertThat(new PaymentTarget.Physical("quote-1").quoteId()).isEqualTo("quote-1");
    }

    @Test
    void physical_rejects_null_or_blank_quote_id() {
        assertThatThrownBy(() -> new PaymentTarget.Physical(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentTarget.Physical(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void virtual_exposes_the_plan_id() {
        assertThat(new PaymentTarget.Virtual("plan-1").planId()).isEqualTo("plan-1");
    }

    @Test
    void virtual_rejects_null_or_blank_plan_id() {
        assertThatThrownBy(() -> new PaymentTarget.Virtual(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentTarget.Virtual(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
