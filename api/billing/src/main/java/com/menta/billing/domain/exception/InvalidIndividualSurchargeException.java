package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when {@code individualSurchargePercent} is zero or negative
 * (US-BILLING-009 escenario 2). Validated in {@link
 * com.menta.billing.domain.model.PhysicalCoursePricing}'s constructor so an
 * invalid value can never reach the repository — no revision is ever
 * created for a rejected update.
 */
public class InvalidIndividualSurchargeException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_INDIVIDUAL_SURCHARGE";

    public InvalidIndividualSurchargeException() {
        super(ERROR_CODE, "individualSurchargePercent must be greater than zero");
    }
}
