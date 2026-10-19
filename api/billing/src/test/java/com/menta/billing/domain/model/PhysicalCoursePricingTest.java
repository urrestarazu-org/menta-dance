package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.domain.exception.InvalidIndividualSurchargeException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PhysicalCoursePricingTest {

    private static final String COURSE_ID = "course-1";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static Money price(String amount) {
        return Money.of(new BigDecimal(amount), "ARS");
    }

    @Test
    void createFirstVersion_starts_at_version_one() {
        PhysicalCoursePricing pricing =
            PhysicalCoursePricing.createFirstVersion(COURSE_ID, price("100.00"), new BigDecimal("10"), NOW);

        assertThat(pricing.getVersion()).isEqualTo(1);
        assertThat(pricing.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(pricing.getMonthlyPrice()).isEqualTo(price("100.00"));
        assertThat(pricing.getIndividualSurchargePercent()).isEqualByComparingTo("10");
        assertThat(pricing.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void revise_increments_the_version_and_keeps_the_course_id() {
        PhysicalCoursePricing first =
            PhysicalCoursePricing.createFirstVersion(COURSE_ID, price("100.00"), new BigDecimal("10"), NOW);

        Instant later = NOW.plusSeconds(60);
        PhysicalCoursePricing revised = first.revise(price("120.00"), new BigDecimal("15"), later);

        assertThat(revised.getVersion()).isEqualTo(2);
        assertThat(revised.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(revised.getMonthlyPrice()).isEqualTo(price("120.00"));
        assertThat(revised.getIndividualSurchargePercent()).isEqualByComparingTo("15");
        assertThat(revised.getUpdatedAt()).isEqualTo(later);
        // The previous version is untouched — used by the use case to build the audit "previousValue".
        assertThat(first.getVersion()).isEqualTo(1);
    }

    @Test
    void reconstitute_preserves_an_already_persisted_version() {
        PhysicalCoursePricing pricing =
            PhysicalCoursePricing.reconstitute(COURSE_ID, price("100.00"), new BigDecimal("10"), 7, NOW);

        assertThat(pricing.getVersion()).isEqualTo(7);
    }

    @Test
    void a_zero_surcharge_is_rejected() {
        assertThatThrownBy(() ->
            PhysicalCoursePricing.createFirstVersion(COURSE_ID, price("100.00"), BigDecimal.ZERO, NOW)
        ).isInstanceOf(InvalidIndividualSurchargeException.class);
    }

    @Test
    void a_negative_surcharge_is_rejected() {
        assertThatThrownBy(() ->
            PhysicalCoursePricing.createFirstVersion(COURSE_ID, price("100.00"), new BigDecimal("-1"), NOW)
        ).isInstanceOf(InvalidIndividualSurchargeException.class);
    }

    @Test
    void a_null_surcharge_is_rejected() {
        assertThatThrownBy(() -> PhysicalCoursePricing.createFirstVersion(COURSE_ID, price("100.00"), null, NOW))
            .isInstanceOf(InvalidIndividualSurchargeException.class);
    }

    @Test
    void revising_with_an_invalid_surcharge_does_not_mutate_the_existing_version() {
        PhysicalCoursePricing first =
            PhysicalCoursePricing.createFirstVersion(COURSE_ID, price("100.00"), new BigDecimal("10"), NOW);

        assertThatThrownBy(() -> first.revise(price("120.00"), BigDecimal.ZERO, NOW.plusSeconds(1)))
            .isInstanceOf(InvalidIndividualSurchargeException.class);
        assertThat(first.getVersion()).isEqualTo(1);
        assertThat(first.getIndividualSurchargePercent()).isEqualByComparingTo("10");
    }
}
