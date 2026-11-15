package com.menta.billing.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanPaymentMethodJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
        List<PlanPaymentMethodJpaEntity> methods = List.of(
            new PlanPaymentMethodJpaEntity(id, "MERCADO_PAGO"),
            new PlanPaymentMethodJpaEntity(id, "BANK_TRANSFER")
        );

        Plan plan = PlanJpaMapper.toDomain(entity, courses, methods);

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
        assertThat(plan.getPaymentMethods())
            .containsExactlyInAnyOrder(PaymentMethod.MERCADO_PAGO, PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void maps_an_entity_with_no_courses() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        PlanJpaEntity entity = new PlanJpaEntity(
            id, "Plan Anual", "Acceso anual", BigDecimal.ZERO, "ARS",
            365, false, PlanStatus.INACTIVE, "Términos", "Política de cancelación", now, now
        );

        Plan plan = PlanJpaMapper.toDomain(
            entity, List.of(), List.of(new PlanPaymentMethodJpaEntity(id, "MERCADO_PAGO"))
        );

        assertThat(plan.getCourses()).isEmpty();
    }

    /** A hand-inserted plan row with no method rows must not break the whole catalog read. */
    @Test
    void defaults_to_mercado_pago_when_a_plan_declares_no_payment_methods() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        PlanJpaEntity entity = new PlanJpaEntity(
            id, "Plan", "Desc", BigDecimal.ONE, "ARS",
            30, false, PlanStatus.ACTIVE, "T", "C", now, now
        );

        Plan plan = PlanJpaMapper.toDomain(entity, List.of(), List.of());

        assertThat(plan.getPaymentMethods()).containsExactly(PaymentMethod.MERCADO_PAGO);
    }

    private static Plan newPlan(PlanId id, List<PlanCourse> courses, Set<PaymentMethod> methods) {
        return new Plan(
            id, "Plan Mensual", "Acceso mensual", Money.of(new BigDecimal("15000.00"), "ARS"), 30, true,
            PlanStatus.INACTIVE, "Términos", "Política de cancelación", courses, methods
        );
    }

    @Test
    void flattens_a_new_plan_into_its_row_stamped_with_the_creation_instant() {
        PlanId id = PlanId.generate();
        Instant createdAt = Instant.parse("2026-11-15T12:00:00Z");

        PlanJpaEntity entity = PlanJpaMapper.toEntity(
            newPlan(id, List.of(), Set.of(PaymentMethod.MERCADO_PAGO)), createdAt
        );

        assertThat(entity.getId()).isEqualTo(id.getValue());
        assertThat(entity.getName()).isEqualTo("Plan Mensual");
        assertThat(entity.getDescription()).isEqualTo("Acceso mensual");
        assertThat(entity.getPrice()).isEqualByComparingTo("15000.00");
        assertThat(entity.getCurrency()).isEqualTo("ARS");
        assertThat(entity.getDurationDays()).isEqualTo(30);
        assertThat(entity.isFeatured()).isTrue();
        assertThat(entity.getStatus()).isEqualTo(PlanStatus.INACTIVE);
        assertThat(entity.getTermsAndConditions()).isEqualTo("Términos");
        assertThat(entity.getCancellationPolicy()).isEqualTo("Política de cancelación");
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(createdAt);
    }

    @Test
    void flattens_the_courses_of_a_plan_into_one_row_each_in_the_plan_order() {
        PlanId id = PlanId.generate();
        Plan plan = newPlan(
            id, List.of(PlanCourse.of("course-2"), PlanCourse.of("course-1")), Set.of(PaymentMethod.MERCADO_PAGO)
        );

        List<PlanCourseJpaEntity> rows = PlanJpaMapper.toCourseEntities(plan);

        assertThat(rows).extracting(PlanCourseJpaEntity::getPlanId).containsOnly(id.getValue());
        assertThat(rows).extracting(PlanCourseJpaEntity::getCourseId).containsExactly("course-2", "course-1");
    }

    @Test
    void flattens_the_payment_methods_of_a_plan_into_one_row_each_by_name() {
        PlanId id = PlanId.generate();
        Plan plan = newPlan(id, List.of(), Set.of(PaymentMethod.MERCADO_PAGO, PaymentMethod.BANK_TRANSFER));

        List<PlanPaymentMethodJpaEntity> rows = PlanJpaMapper.toPaymentMethodEntities(plan);

        assertThat(rows).extracting(PlanPaymentMethodJpaEntity::getPlanId).containsOnly(id.getValue());
        assertThat(rows).extracting(PlanPaymentMethodJpaEntity::getPaymentMethod)
            .containsExactlyInAnyOrder("MERCADO_PAGO", "BANK_TRANSFER");
    }
}
