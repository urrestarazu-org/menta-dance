package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown either when {@code CoveragePlanner} cannot fill the quote's
 * {@code scheduledSessionCount} with eligible sessions (design A6, D5, #41
 * US-PHYSICAL-004), or when the real atomic capacity hold itself refuses a
 * claim (#208, design B2/B4/D2).
 *
 * <p>Since #208, a {@code 409} answer from this exception is a
 * <strong>real capacity guarantee, not a best-effort courtesy</strong>: the
 * hold is attempted — and, on success, reserves every eligible session for
 * this payment — before any {@code Payment} row is ever created. A request
 * that instead receives {@code 201} is guaranteed to have reserved every
 * session its plan covers, so no other buyer can take that spot before
 * confirmation. This exception kept its original #41 error code across that
 * change (design D2), so the response shape never changed — only its
 * truthfulness did.</p>
 */
public class PhysicalCapacityUnavailableException extends BusinessException {

    private static final String ERROR_CODE = "CAPACITY_UNAVAILABLE";

    public PhysicalCapacityUnavailableException() {
        super(ERROR_CODE, "No eligible session currently has available capacity for this quote");
    }
}
