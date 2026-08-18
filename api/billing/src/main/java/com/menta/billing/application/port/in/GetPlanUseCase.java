package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.PlanDetailResult;

/** Application-layer entry point for a single plan's detail (US-BILLING-001 escenario 4/5). */
public interface GetPlanUseCase {

    /**
     * @throws com.menta.billing.domain.exception.PlanNotFoundException if absent or not ACTIVE.
     * @throws com.menta.billing.domain.exception.PlanRateLimitedException if the caller's IP budget is spent.
     */
    PlanDetailResult getPlan(String planId, String clientFingerprint);
}
