package com.menta.physical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.physical.domain.exception.InvalidSessionRangeException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InstantRangeTest {

    private static final Duration MAX = Duration.ofDays(92);
    // 2026-11-12 00:00 in Buenos Aires.
    private static final Instant FROM = Instant.parse("2026-11-12T03:00:00Z");

    @Test
    void a_range_that_starts_before_it_ends_is_built_as_given() {
        Instant to = Instant.parse("2026-11-13T03:00:00Z");

        InstantRange range = InstantRange.within(FROM, to, MAX);

        assertThat(range.from()).isEqualTo(FROM);
        assertThat(range.to()).isEqualTo(to);
    }

    @Test
    void the_smallest_possible_range_of_one_nanosecond_is_valid() {
        assertThat(InstantRange.within(FROM, FROM.plusNanos(1), MAX).to()).isEqualTo(FROM.plusNanos(1));
    }

    @Test
    void exactly_the_maximum_span_is_accepted() {
        assertThat(InstantRange.within(FROM, FROM.plus(MAX), MAX).to()).isEqualTo(FROM.plus(MAX));
    }

    @Test
    void one_nanosecond_over_the_maximum_span_is_rejected_naming_the_maximum_in_days() {
        assertThatThrownBy(() -> InstantRange.within(FROM, FROM.plus(MAX).plusNanos(1), MAX))
            .isInstanceOfSatisfying(InvalidSessionRangeException.class, exception -> {
                assertThat(exception.getMaxDays()).isEqualTo(92);
                assertThat(exception.getErrorCode()).isEqualTo("INVALID_REQUEST");
                assertThat(exception.getMessage()).contains("at most 92 days");
            });
    }

    @Test
    void an_empty_range_is_rejected() {
        assertThatThrownBy(() -> InstantRange.within(FROM, FROM, MAX))
            .isInstanceOf(InvalidSessionRangeException.class);
    }

    @Test
    void an_inverted_range_is_rejected() {
        assertThatThrownBy(() -> InstantRange.within(FROM, FROM.minusNanos(1), MAX))
            .isInstanceOf(InvalidSessionRangeException.class);
    }

    @Test
    void the_widest_representable_range_is_rejected_without_overflowing() {
        assertThatThrownBy(() -> InstantRange.within(Instant.MIN, Instant.MAX, MAX))
            .isInstanceOf(InvalidSessionRangeException.class);
    }
}
