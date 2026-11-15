package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanPaymentMethodJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Manual mapping between {@link PlanJpaEntity} and the domain {@link Plan}. */
public final class PlanJpaMapper {

    private PlanJpaMapper() {
    }

    /**
     * Rebuilds the plan from its row plus its already loaded course and payment-method rows.
     *
     * <p>A plan without any payment-method row is treated as accepting only {@code MERCADO_PAGO}.
     */
    public static Plan toDomain(
        PlanJpaEntity entity, List<PlanCourseJpaEntity> courses, List<PlanPaymentMethodJpaEntity> paymentMethods
    ) {
        return new Plan(
            PlanId.of(entity.getId()),
            entity.getName(),
            entity.getDescription(),
            Money.of(entity.getPrice(), entity.getCurrency()),
            entity.getDurationDays(),
            entity.isFeatured(),
            entity.getStatus(),
            entity.getTermsAndConditions(),
            entity.getCancellationPolicy(),
            courses.stream().map(course -> PlanCourse.of(course.getCourseId())).toList(),
            toPaymentMethods(paymentMethods)
        );
    }

    /**
     * Flattens a new plan into its {@code billing_plans} row (#762), splitting the price into an
     * amount and a currency; {@code createdAt} is both the creation and the last-update instant.
     */
    public static PlanJpaEntity toEntity(Plan plan, Instant createdAt) {
        return new PlanJpaEntity(
            plan.getId().getValue(),
            plan.getName(),
            plan.getDescription(),
            plan.getPrice().getAmount(),
            plan.getPrice().getCurrency(),
            plan.getDurationDays(),
            plan.isFeatured(),
            plan.getStatus(),
            plan.getTermsAndConditions(),
            plan.getCancellationPolicy(),
            createdAt,
            createdAt
        );
    }

    /** One {@code billing_plan_courses} row per course of the plan, in the plan's own order. */
    public static List<PlanCourseJpaEntity> toCourseEntities(Plan plan) {
        UUID planId = plan.getId().getValue();
        return plan.getCourses().stream()
            .map(course -> new PlanCourseJpaEntity(planId, course.getCourseId()))
            .toList();
    }

    /** One {@code billing_plan_payment_methods} row per accepted method, stored by enum name. */
    public static List<PlanPaymentMethodJpaEntity> toPaymentMethodEntities(Plan plan) {
        UUID planId = plan.getId().getValue();
        return plan.getPaymentMethods().stream()
            .map(method -> new PlanPaymentMethodJpaEntity(planId, method.name()))
            .toList();
    }

    /**
     * A plan row with no method rows is treated as Mercado Pago only. {@code
     * Plan} refuses an empty set by construction, and V14 backfills every
     * pre-existing plan — this keeps a hand-inserted row from failing the
     * whole catalog read instead of just its own checkout.
     */
    private static Set<PaymentMethod> toPaymentMethods(List<PlanPaymentMethodJpaEntity> paymentMethods) {
        if (paymentMethods.isEmpty()) {
            return Set.of(PaymentMethod.MERCADO_PAGO);
        }
        return paymentMethods.stream()
            .map(method -> PaymentMethod.valueOf(method.getPaymentMethod()))
            .collect(Collectors.toUnmodifiableSet());
    }
}
