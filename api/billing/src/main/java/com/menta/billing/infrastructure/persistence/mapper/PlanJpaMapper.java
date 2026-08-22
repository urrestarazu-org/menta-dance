package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanPaymentMethodJpaEntity;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Manual mapping between {@link PlanJpaEntity} and the domain {@link Plan}. */
public final class PlanJpaMapper {

    private PlanJpaMapper() {
    }

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
