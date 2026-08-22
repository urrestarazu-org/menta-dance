package com.menta.billing.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PhysicalCoursePricingJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        Instant updatedAt = Instant.now();

        PhysicalCoursePricingJpaEntity entity = new PhysicalCoursePricingJpaEntity(
            "course-1", new BigDecimal("150.00"), "ARS", new BigDecimal("10.00"), 3, updatedAt
        );

        assertThat(entity.getCourseId()).isEqualTo("course-1");
        assertThat(entity.getMonthlyPrice()).isEqualByComparingTo("150.00");
        assertThat(entity.getMonthlyPriceCurrency()).isEqualTo("ARS");
        assertThat(entity.getIndividualSurchargePercent()).isEqualByComparingTo("10.00");
        assertThat(entity.getVersion()).isEqualTo(3);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
