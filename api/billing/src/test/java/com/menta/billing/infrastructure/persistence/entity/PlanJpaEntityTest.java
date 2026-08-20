package com.menta.billing.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.PlanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant updatedAt = createdAt.plusSeconds(60);

        PlanJpaEntity entity = new PlanJpaEntity(
            id, "Plan Mensual", "Acceso mensual", BigDecimal.valueOf(19.99), "ARS",
            30, true, PlanStatus.ACTIVE, "Términos", "Política de cancelación", createdAt, updatedAt
        );

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getName()).isEqualTo("Plan Mensual");
        assertThat(entity.getDescription()).isEqualTo("Acceso mensual");
        assertThat(entity.getPrice()).isEqualByComparingTo("19.99");
        assertThat(entity.getCurrency()).isEqualTo("ARS");
        assertThat(entity.getDurationDays()).isEqualTo(30);
        assertThat(entity.isFeatured()).isTrue();
        assertThat(entity.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(entity.getTermsAndConditions()).isEqualTo("Términos");
        assertThat(entity.getCancellationPolicy()).isEqualTo("Política de cancelación");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
