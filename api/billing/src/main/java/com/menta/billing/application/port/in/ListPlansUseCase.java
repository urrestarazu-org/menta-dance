package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.PlanSummaryResult;
import java.util.List;

/** Application-layer entry point for listing active plans (US-BILLING-001 escenario 1/2/3). */
public interface ListPlansUseCase {

    /** @throws com.menta.billing.domain.exception.PlanRateLimitedException if the caller's IP budget is spent. */
    List<PlanSummaryResult> listActivePlans(String clientFingerprint);
}
