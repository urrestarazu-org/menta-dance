package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProviderOutcomeTest {

    private static Money amount() {
        return Money.of(BigDecimal.TEN, "ARS");
    }

    @Test
    void exposes_every_field() {
        ProviderOutcome outcome = new ProviderOutcome("approved", amount(), "ext-1", "merchant-1");

        assertThat(outcome.providerStatus()).isEqualTo("approved");
        assertThat(outcome.amount()).isEqualTo(amount());
        assertThat(outcome.externalReference()).isEqualTo("ext-1");
        assertThat(outcome.merchantAccountId()).isEqualTo("merchant-1");
    }

    @Test
    void rejects_null_fields() {
        assertThatThrownBy(() -> new ProviderOutcome(null, amount(), "ext-1", "merchant-1"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProviderOutcome("approved", null, "ext-1", "merchant-1"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProviderOutcome("approved", amount(), null, "merchant-1"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProviderOutcome("approved", amount(), "ext-1", null))
            .isInstanceOf(NullPointerException.class);
    }
}
