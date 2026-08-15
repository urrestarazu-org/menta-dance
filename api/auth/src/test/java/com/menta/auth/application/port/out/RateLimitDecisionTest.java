package com.menta.auth.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitDecisionTest {

    @Test
    void allowed_decision_carries_no_retry_after() {
        RateLimitDecision decision = RateLimitDecision.allowed();

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getRetryAfter()).isZero();
    }

    @Test
    void limited_decision_carries_positive_retry_after() {
        RateLimitDecision decision = RateLimitDecision.limited(Duration.ofSeconds(30));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getRetryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void limited_decision_requires_positive_retry_after() {
        assertThatThrownBy(() -> RateLimitDecision.limited(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("retryAfter");
    }

    @Test
    void limited_decision_rejects_negative_retry_after() {
        assertThatThrownBy(() -> RateLimitDecision.limited(Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("negative");
    }
}
