package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an INSTRUCTOR attempts to update a physical course's pricing
 * for a course they do not teach (US-BILLING-009). An ADMIN never triggers
 * this.
 */
public class PricingNotOwnedException extends BusinessException {

    private static final String ERROR_CODE = "PRICING_NOT_OWNED";

    public PricingNotOwnedException() {
        super(ERROR_CODE, "You do not own this course's pricing");
    }
}
