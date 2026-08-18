package com.menta.billing.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitDecisionTest {

    @Test
    void allowed_reports_allowed_true() {
        assertThat(RateLimitDecision.allowed().isAllowed()).isTrue();
    }

    @Test
    void allowed_has_no_retry_after() {
        assertThatThrownBy(() -> RateLimitDecision.allowed().getRetryAfter())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void limited_carries_the_retry_window() {
        RateLimitDecision decision = RateLimitDecision.limited(Duration.ofSeconds(30));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getRetryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void limited_rejects_a_null_or_negative_duration() {
        assertThatThrownBy(() -> RateLimitDecision.limited(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RateLimitDecision.limited(Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
