package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import java.util.List;

/** Manual mapping between {@link PlanJpaEntity} and the domain {@link Plan}. */
public final class PlanJpaMapper {

    private PlanJpaMapper() {
    }

    public static Plan toDomain(PlanJpaEntity entity, List<PlanCourseJpaEntity> courses) {
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
            courses.stream().map(course -> PlanCourse.of(course.getCourseId())).toList()
        );
    }
}
