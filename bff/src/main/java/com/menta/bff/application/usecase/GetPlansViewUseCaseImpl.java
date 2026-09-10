package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PlanSummary;
import com.menta.bff.application.port.out.BillingApiClient;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link GetPlansViewUseCase}.
 * <p>
 * Pure pass-through, no try/catch: {@link BillingApiClient#getPlans()} is
 * the plans page's only upstream dependency, so any {@link
 * BillingApiClient.NotFoundException} or {@link
 * BillingApiClient.ServiceUnavailableException} it throws propagates
 * untranslated, exactly mirroring the catalog-call half of {@link
 * GetCourseDetailViewUseCaseImpl} (tasks.md 4.4).
 * </p>
 */
public class GetPlansViewUseCaseImpl implements GetPlansViewUseCase {

    private final BillingApiClient billingApiClient;

    /**
     * Constructor for dependency injection.
     *
     * @param billingApiClient HTTP client for the Billing API
     */
    public GetPlansViewUseCaseImpl(BillingApiClient billingApiClient) {
        this.billingApiClient = Objects.requireNonNull(billingApiClient, "billingApiClient cannot be null");
    }

    @Override
    public List<PlanSummary> execute() {
        return billingApiClient.getPlans();
    }
}
