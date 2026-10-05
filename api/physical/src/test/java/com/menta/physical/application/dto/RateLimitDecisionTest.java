package com.menta.physical.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitDecisionTest {

    @Test
    void allowed_has_no_dimension_and_no_retry_window() {
        RateLimitDecision decision = RateLimitDecision.allowed();

        assertThat(decision.isAllowed()).isTrue();
        assertThatThrownBy(decision::getRetryAfter).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(decision::getDimension).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void limited_carries_the_tripped_dimension_and_the_retry_window() {
        RateLimitDecision decision =
            RateLimitDecision.limited(RateLimitDimension.DEVICE, Duration.ofSeconds(7));

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getDimension()).isEqualTo(RateLimitDimension.DEVICE);
        assertThat(decision.getRetryAfter()).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void limited_rejects_a_null_dimension() {
        assertThatThrownBy(() -> RateLimitDecision.limited(null, Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limited_rejects_a_null_or_negative_retry_window() {
        assertThatThrownBy(() -> RateLimitDecision.limited(RateLimitDimension.IP, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
            () -> RateLimitDecision.limited(RateLimitDimension.IP, Duration.ofSeconds(-1))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dimensions_expose_a_stable_lowercase_tag() {
        assertThat(RateLimitDimension.IP.tag()).isEqualTo("ip");
        assertThat(RateLimitDimension.DEVICE.tag()).isEqualTo("device");
    }
}
