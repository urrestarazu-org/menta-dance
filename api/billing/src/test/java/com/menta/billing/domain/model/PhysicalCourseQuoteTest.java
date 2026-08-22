package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.domain.exception.IndividualSurchargeTooSmallException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PhysicalCourseQuoteTest {

    private static final String COURSE_ID = "course-1";
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    private static PhysicalCoursePricing pricing(String monthlyPrice, String surcharge, int version) {
        return PhysicalCoursePricing.reconstitute(
            COURSE_ID, Money.of(new BigDecimal(monthlyPrice), "ARS"), new BigDecimal(surcharge), version,
            NOW.minusSeconds(3600)
        );
    }

    @Test
    void monthly_uses_the_monthly_price_verbatim_and_has_no_selected_session() {
        PhysicalCoursePricing pricing = pricing("150.00", "10", 2);

        PhysicalCourseQuote quote =
            PhysicalCourseQuote.monthly(COURSE_ID, pricing, 8, QuoteAvailability.AVAILABLE, NOW);

        assertThat(quote.getPurchaseType()).isEqualTo(PurchaseType.MONTHLY);
        assertThat(quote.getAmount()).isEqualTo(Money.of(new BigDecimal("150.00"), "ARS"));
        assertThat(quote.getSelectedSessionId()).isNull();
        assertThat(quote.getScheduledSessionCount()).isEqualTo(8);
        assertThat(quote.getPricingVersion()).isEqualTo(2);
        assertThat(quote.getAvailability()).isEqualTo(QuoteAvailability.AVAILABLE);
        assertThat(quote.getCreatedAt()).isEqualTo(NOW);
        assertThat(quote.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void individual_divides_applies_surcharge_and_rounds_half_up() {
        // 100.00 / 8 = 12.50 per session; +10% => 13.75
        PhysicalCoursePricing pricing = pricing("100.00", "10", 1);

        PhysicalCourseQuote quote = PhysicalCourseQuote.individual(
            COURSE_ID, pricing, 8, "session-1", QuoteAvailability.AVAILABLE, NOW
        );

        assertThat(quote.getPurchaseType()).isEqualTo(PurchaseType.INDIVIDUAL);
        assertThat(quote.getAmount()).isEqualTo(Money.of(new BigDecimal("13.75"), "ARS"));
        assertThat(quote.getSelectedSessionId()).isEqualTo("session-1");
        assertThat(quote.getScheduledSessionCount()).isEqualTo(8);
    }

    @Test
    void individual_keeps_scheduled_session_count_as_the_period_divisor_even_though_only_one_session_is_returned() {
        PhysicalCoursePricing pricing = pricing("120.00", "15", 1);

        PhysicalCourseQuote quote = PhysicalCourseQuote.individual(
            COURSE_ID, pricing, 12, "session-42", QuoteAvailability.UNAVAILABLE, NOW
        );

        assertThat(quote.getScheduledSessionCount()).isEqualTo(12);
        assertThat(quote.getSelectedSessionId()).isEqualTo("session-42");
        assertThat(quote.getAvailability()).isEqualTo(QuoteAvailability.UNAVAILABLE);
    }

    @Test
    void individual_rejects_a_surcharge_invisible_after_rounding() {
        // A cheap course, many sessions, tiny surcharge: per-session and individual
        // price round to the exact same two-decimal value.
        // 10.00 / 100 = 0.10 per session; +0.5% => 0.1005, rounds to 0.10 too.
        PhysicalCoursePricing pricing = pricing("10.00", "0.5", 1);

        assertThatThrownBy(() ->
            PhysicalCourseQuote.individual(COURSE_ID, pricing, 100, "session-1", QuoteAvailability.AVAILABLE, NOW)
        ).isInstanceOf(IndividualSurchargeTooSmallException.class);
    }

    @Test
    void individual_accepts_a_surcharge_that_clears_the_rounding_boundary_by_exactly_one_minor_unit() {
        // 100.00 / 4 = 25.00 per session; a surcharge producing exactly 25.01 must pass.
        // 25.00 * (1 + x/100) = 25.01 => x = 0.04
        PhysicalCoursePricing pricing = pricing("100.00", "0.04", 1);

        PhysicalCourseQuote quote = PhysicalCourseQuote.individual(
            COURSE_ID, pricing, 4, "session-1", QuoteAvailability.AVAILABLE, NOW
        );

        assertThat(quote.getAmount()).isEqualTo(Money.of(new BigDecimal("25.01"), "ARS"));
    }

    @Test
    void reconstitute_preserves_every_field() {
        Instant createdAt = NOW.minusSeconds(1800);
        Instant expiresAt = createdAt.plus(Duration.ofHours(1));

        PhysicalCourseQuote quote = PhysicalCourseQuote.reconstitute(
            java.util.UUID.randomUUID(), COURSE_ID, PurchaseType.INDIVIDUAL,
            Money.of(new BigDecimal("100.00"), "ARS"), new BigDecimal("10"), 3, 8, "session-7",
            Money.of(new BigDecimal("13.75"), "ARS"), QuoteAvailability.AVAILABLE, createdAt, expiresAt
        );

        assertThat(quote.getPricingVersion()).isEqualTo(3);
        assertThat(quote.getScheduledSessionCount()).isEqualTo(8);
        assertThat(quote.getSelectedSessionId()).isEqualTo("session-7");
        assertThat(quote.getCreatedAt()).isEqualTo(createdAt);
        assertThat(quote.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void a_zero_scheduled_session_count_is_rejected() {
        PhysicalCoursePricing pricing = pricing("100.00", "10", 1);

        assertThatThrownBy(() ->
            PhysicalCourseQuote.monthly(COURSE_ID, pricing, 0, QuoteAvailability.AVAILABLE, NOW)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
