package com.menta.billing.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanJpaMapperTest {

    @Test
    void maps_an_entity_and_its_courses_to_the_domain_model() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant updatedAt = createdAt.plusSeconds(60);
        PlanJpaEntity entity = new PlanJpaEntity(
            id, "Plan Mensual", "Acceso mensual", BigDecimal.valueOf(19.99), "ARS",
            30, true, PlanStatus.ACTIVE, "Términos", "Política de cancelación", createdAt, updatedAt
        );
        List<PlanCourseJpaEntity> courses = List.of(
            new PlanCourseJpaEntity(id, "course-1"), new PlanCourseJpaEntity(id, "course-2")
        );

        Plan plan = PlanJpaMapper.toDomain(entity, courses);

        assertThat(plan.getId().getValue()).isEqualTo(id);
        assertThat(plan.getName()).isEqualTo("Plan Mensual");
        assertThat(plan.getDescription()).isEqualTo("Acceso mensual");
        assertThat(plan.getPrice().getAmount()).isEqualByComparingTo("19.99");
        assertThat(plan.getPrice().getCurrency()).isEqualTo("ARS");
        assertThat(plan.getDurationDays()).isEqualTo(30);
        assertThat(plan.isFeatured()).isTrue();
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(plan.getTermsAndConditions()).isEqualTo("Términos");
        assertThat(plan.getCancellationPolicy()).isEqualTo("Política de cancelación");
        assertThat(plan.getCourses()).extracting(course -> course.getCourseId())
            .containsExactly("course-1", "course-2");
    }

    @Test
    void maps_an_entity_with_no_courses() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        PlanJpaEntity entity = new PlanJpaEntity(
            id, "Plan Anual", "Acceso anual", BigDecimal.ZERO, "ARS",
            365, false, PlanStatus.INACTIVE, "Términos", "Política de cancelación", now, now
        );

        Plan plan = PlanJpaMapper.toDomain(entity, List.of());

        assertThat(plan.getCourses()).isEmpty();
    }
}
