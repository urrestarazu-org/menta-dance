package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a plan does not exist or is not {@code ACTIVE} (US-BILLING-001
 * escenario 5). Deliberately does not distinguish the two cases: a caller
 * must not be able to tell "never existed" from "exists but inactive", the
 * same anti-enumeration discipline auth applies to its own not-found paths.
 */
public class PlanNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "PLAN_NOT_FOUND";

    public PlanNotFoundException() {
        super(ERROR_CODE, "Plan not found or not available");
    }
}
