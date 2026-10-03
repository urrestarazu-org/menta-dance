package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.PlanDetailResult;
import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.port.in.GetPlanUseCase;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.domain.exception.PlanNotFoundException;
import com.menta.billing.domain.exception.PlanRateLimitedException;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanId;
import java.util.List;
import java.util.Map;

/** Implementation of {@link GetPlanUseCase}. */
public class GetPlanUseCaseImpl implements GetPlanUseCase {

    private final PlanRepository planRepository;
    private final CourseCatalogPort courseCatalogPort;
    private final BillingPlansRateLimitPort rateLimitPort;

    public GetPlanUseCaseImpl(
        PlanRepository planRepository, CourseCatalogPort courseCatalogPort,
        BillingPlansRateLimitPort rateLimitPort
    ) {
        this.planRepository = planRepository;
        this.courseCatalogPort = courseCatalogPort;
        this.rateLimitPort = rateLimitPort;
    }

    @Override
    public PlanDetailResult getPlan(String planId, String clientFingerprint) {
        RateLimitDecision decision = rateLimitPort.consume(clientFingerprint);
        if (!decision.isAllowed()) {
            throw new PlanRateLimitedException(decision.getRetryAfter());
        }
        Plan plan = planRepository.findActiveById(parseId(planId)).orElseThrow(PlanNotFoundException::new);
        return toDetail(plan, PlanCourseResolver.resolveNames(List.of(plan), courseCatalogPort));
    }

    private static PlanId parseId(String planId) {
        try {
            return PlanId.of(planId);
        } catch (IllegalArgumentException malformed) {
            // A malformed id is exactly as informative as a well-formed one
            // that does not exist — both must read as "not found".
            throw new PlanNotFoundException();
        }
    }

    private static PlanDetailResult toDetail(Plan plan, Map<String, String> courseNames) {
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
