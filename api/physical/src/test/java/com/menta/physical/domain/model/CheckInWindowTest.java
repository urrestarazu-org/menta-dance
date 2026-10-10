package com.menta.physical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CheckInWindowTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-10-10T22:00:00Z");
    private final CheckInWindow window = new CheckInWindow(Duration.ofMinutes(30), Duration.ofHours(2));

    @Test
    void both_bounds_are_inclusive() {
        assertThat(window.isOpenAt(SCHEDULED_AT, Instant.parse("2026-10-10T21:30:00Z"))).isTrue();
        assertThat(window.isOpenAt(SCHEDULED_AT, Instant.parse("2026-10-11T00:00:00Z"))).isTrue();
    }

    @Test
    void is_closed_one_second_before_the_opening_and_after_the_closing() {
        assertThat(window.isOpenAt(SCHEDULED_AT, Instant.parse("2026-10-10T21:29:59Z"))).isFalse();
        assertThat(window.isOpenAt(SCHEDULED_AT, Instant.parse("2026-10-11T00:00:01Z"))).isFalse();
    }

    @Test
    void rejects_missing_or_negative_bounds() {
        assertThatThrownBy(() -> new CheckInWindow(null, Duration.ZERO))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CheckInWindow(Duration.ZERO, null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CheckInWindow(Duration.ofMinutes(-1), Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CheckInWindow(Duration.ZERO, Duration.ofMinutes(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
