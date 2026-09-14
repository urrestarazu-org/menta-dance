package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * At checkout time, {@code CoveragePlanner} could not fill the quote's
 * {@code scheduledSessionCount} with sessions that currently read {@code
 * availableSpots > 0} (design A6, D5, #41 US-PHYSICAL-004).
 *
 * <p>This is a <strong>best-effort, read-time courtesy rejection, not a
 * capacity guarantee</strong>: nothing is held or reserved by this check,
 * so a request that passes it may still resolve to {@code EXCEPTION} at
 * confirmation if another buyer takes the last spot first. It exists only
 * to avoid charging for something the system can already see is sold out.
 * A real, hold-backed {@code 409 CAPACITY_UNAVAILABLE} guarantee is future
 * work (#208) — this exception intentionally shares its error code with
 * that future capability so the response shape does not change when the
 * guarantee arrives, only its truthfulness does.</p>
 */
public class PhysicalCapacityUnavailableException extends BusinessException {

    private static final String ERROR_CODE = "CAPACITY_UNAVAILABLE";

    public PhysicalCapacityUnavailableException() {
        super(ERROR_CODE, "No eligible session currently has available capacity for this quote");
    }
}
