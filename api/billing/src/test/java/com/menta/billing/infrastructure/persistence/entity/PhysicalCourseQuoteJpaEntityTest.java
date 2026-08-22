package com.menta.billing.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PhysicalCourseQuoteJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plusSeconds(3600);
        String id = java.util.UUID.randomUUID().toString();

        PhysicalCourseQuoteJpaEntity entity = new PhysicalCourseQuoteJpaEntity(
            id, "course-1", "INDIVIDUAL", new BigDecimal("100.00"), "ARS", new BigDecimal("10.00"), 3, 8,
            "session-1", new BigDecimal("13.75"), "ARS", "AVAILABLE", createdAt, expiresAt
        );

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getCourseId()).isEqualTo("course-1");
        assertThat(entity.getPurchaseType()).isEqualTo("INDIVIDUAL");
        assertThat(entity.getMonthlyPriceAmount()).isEqualByComparingTo("100.00");
        assertThat(entity.getMonthlyPriceCurrency()).isEqualTo("ARS");
        assertThat(entity.getIndividualSurchargePercent()).isEqualByComparingTo("10.00");
        assertThat(entity.getPricingVersion()).isEqualTo(3);
        assertThat(entity.getScheduledSessionCount()).isEqualTo(8);
        assertThat(entity.getSelectedSessionId()).isEqualTo("session-1");
        assertThat(entity.getAmountValue()).isEqualByComparingTo("13.75");
        assertThat(entity.getAmountCurrency()).isEqualTo("ARS");
        assertThat(entity.getAvailability()).isEqualTo("AVAILABLE");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
    }
}
