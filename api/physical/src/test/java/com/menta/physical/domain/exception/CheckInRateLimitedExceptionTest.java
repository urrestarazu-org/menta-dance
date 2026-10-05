package com.menta.physical.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CheckInRateLimitedExceptionTest {

    @Test
    void carries_a_stable_error_code_and_the_retry_window() {
        CheckInRateLimitedException exception =
            new CheckInRateLimitedException(Duration.ofSeconds(42));

        assertThat(exception.getErrorCode()).isEqualTo("CHECK_IN_RATE_LIMITED");
        assertThat(exception.getRetryAfter()).isEqualTo(Duration.ofSeconds(42));
    }

    @Test
    void rejects_a_null_retry_window() {
        assertThatThrownBy(() -> new CheckInRateLimitedException(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_negative_retry_window() {
        assertThatThrownBy(() -> new CheckInRateLimitedException(Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
