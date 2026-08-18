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
        return planRepository.findAllActiveOrderByPriceAsc().stream()
            .map(this::toSummary)
            .toList();
    }

    private PlanSummaryResult toSummary(Plan plan) {
        return new PlanSummaryResult(
            plan.getId().toString(),
            plan.getName(),
            plan.getDescription(),
            plan.getPrice().getAmount(),
            plan.getPrice().getCurrency(),
            plan.getDurationDays(),
            plan.isFeatured(),
            PlanCourseResolver.resolve(plan.getCourses(), courseCatalogPort)
        );
    }
}
