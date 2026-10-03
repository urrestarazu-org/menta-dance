package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.PlanSummaryResult;
import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.port.in.ListPlansUseCase;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.domain.exception.PlanRateLimitedException;
import com.menta.billing.domain.model.Plan;
import java.util.List;
import java.util.Map;

/** Implementation of {@link ListPlansUseCase}. */
public class ListPlansUseCaseImpl implements ListPlansUseCase {

    private final PlanRepository planRepository;
    private final CourseCatalogPort courseCatalogPort;
    private final BillingPlansRateLimitPort rateLimitPort;

    public ListPlansUseCaseImpl(
        PlanRepository planRepository, CourseCatalogPort courseCatalogPort,
        BillingPlansRateLimitPort rateLimitPort
    ) {
        this.planRepository = planRepository;
        this.courseCatalogPort = courseCatalogPort;
        this.rateLimitPort = rateLimitPort;
    }

    @Override
    public List<PlanSummaryResult> listActivePlans(String clientFingerprint) {
        RateLimitDecision decision = rateLimitPort.consume(clientFingerprint);
        if (!decision.isAllowed()) {
            throw new PlanRateLimitedException(decision.getRetryAfter());
        }
        List<Plan> plans = planRepository.findAllActiveOrderByPriceAsc();
        Map<String, String> courseNames = PlanCourseResolver.resolveNames(plans, courseCatalogPort);
        return plans.stream()
            .map(plan -> toSummary(plan, courseNames))
            .toList();
    }

    private static PlanSummaryResult toSummary(Plan plan, Map<String, String> courseNames) {
        return new PlanSummaryResult(
            plan.getId().toString(),
            plan.getName(),
            plan.getDescription(),
            plan.getPrice().getAmount(),
            plan.getPrice().getCurrency(),
            plan.getDurationDays(),
            plan.isFeatured(),
            PlanCourseResolver.toResults(plan.getCourses(), courseNames)
        );
    }
}
