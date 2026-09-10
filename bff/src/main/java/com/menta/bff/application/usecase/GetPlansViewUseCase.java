package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PlanSummary;

import java.util.List;

/**
 * Use case for retrieving the plans page's rendering data.
 * <p>
 * Part of Clean Architecture application layer.
 * </p>
 */
public interface GetPlansViewUseCase {

    /**
     * Retrieves the list of plans to render on the plans page, in upstream
     * response order.
     * <p>
     * This is a pure pass-through to {@link
     * com.menta.bff.application.port.out.BillingApiClient#getPlans()}: no
     * branching, no cross-reference with any other upstream call. Every
     * failure propagates untranslated (design: the plans page has exactly
     * one upstream dependency, so there is nothing to collapse a failure
     * into).
     * </p>
     *
     * @return the list of plans, unchanged from the upstream response order
     * @throws com.menta.bff.application.port.out.BillingApiClient.NotFoundException
     *         if the upstream endpoint reports no plans found (404)
     * @throws com.menta.bff.application.port.out.BillingApiClient.ServiceUnavailableException
     *         if the upstream is unreachable, rate-limited, or errors (429/503)
     */
    List<PlanSummary> execute();
}
