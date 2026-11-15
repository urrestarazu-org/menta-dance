package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.PlanDetailResult;
import com.menta.billing.domain.model.Plan;
import java.util.Map;

/**
 * The public detail projection of a plan, shared by the use case that reads one ({@link
 * GetPlanUseCaseImpl}) and the one that creates one ({@link CreatePlanUseCaseImpl}), so both
 * answer with exactly the same shape.
 */
final class PlanDetails {

    private PlanDetails() {
    }

    /** Projects the plan, naming its courses from {@code courseNames} ({@code null} when absent). */
    static PlanDetailResult of(Plan plan, Map<String, String> courseNames) {
        return new PlanDetailResult(
            plan.getId().toString(),
            plan.getName(),
            plan.getDescription(),
            plan.getPrice().getAmount(),
            plan.getPrice().getCurrency(),
            plan.getDurationDays(),
            plan.isFeatured(),
            plan.getTermsAndConditions(),
            plan.getCancellationPolicy(),
            PlanCourseResolver.toResults(plan.getCourses(), courseNames)
        );
    }
}
