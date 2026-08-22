package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * The plan does not exist, or exists but is not {@code ACTIVE}
 * (US-BILLING-010 escenario 4).
 *
 * <p>Deliberately one exception for both cases — same anti-enumeration
 * discipline the public catalog applies in {@link PlanNotFoundException}: a
 * caller must not be able to probe which plan ids exist.</p>
 */
public class PlanNotAvailableException extends BusinessException {

    private static final String ERROR_CODE = "PLAN_NOT_AVAILABLE";

    public PlanNotAvailableException() {
        super(ERROR_CODE, "Plan not found or not available for subscription");
    }
}
