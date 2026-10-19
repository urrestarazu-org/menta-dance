package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an INDIVIDUAL quote's rounded price does not exceed the
 * rounded per-session price (no surcharge) by at least one currency minor
 * unit (US-BILLING-006, business decision confirmed with the user) — a real
 * situation for a cheap course with many monthly sessions and a small
 * surcharge, not merely a defensive guard. No quote is persisted.
 */
public class IndividualSurchargeTooSmallException extends BusinessException {

    private static final String ERROR_CODE = "INDIVIDUAL_SURCHARGE_TOO_SMALL";

    public IndividualSurchargeTooSmallException() {
        super(ERROR_CODE, "The individual surcharge is not visible after rounding");
    }
}
