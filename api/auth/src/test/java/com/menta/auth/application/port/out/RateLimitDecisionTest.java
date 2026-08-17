package com.menta.auth.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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

    /**
     * Neither public factory can reach this branch — {@link RateLimitDecision#allowed()}
     * always pairs {@code true} with {@link Duration#ZERO} and {@link RateLimitDecision#limited}
     * always passes {@code false}. The invariant is still worth enforcing directly: it is what
     * makes an "allowed decision with a retryAfter" unrepresentable even for a future caller.
     */
    @Test
    void constructor_rejects_an_allowed_decision_carrying_a_retry_after() throws Exception {
        Constructor<RateLimitDecision> constructor =
            RateLimitDecision.class.getDeclaredConstructor(boolean.class, Duration.class);
        constructor.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                constructor.newInstance(true, Duration.ofSeconds(5));
            } catch (InvocationTargetException wrapped) {
                throw wrapped.getCause();
            }
        })
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot carry a retryAfter");
    }
}
