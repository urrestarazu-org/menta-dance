package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a MONTHLY physical purchase would add no session for the
 * student (#541): every session the monthly would cover is already assigned
 * to them. Charging the full monthly price for nothing is refused outright —
 * no quote is persisted at quote time, and no hold or {@code Payment} is
 * created at checkout.
 *
 * <p>Partial ownership is not this case: the monthly still adds at least one
 * session and keeps its full price (no proration — product decision #541).</p>
 */
public class NoNewSessionsToPurchaseException extends BusinessException {

    private static final String ERROR_CODE = "NO_NEW_SESSIONS";

    public NoNewSessionsToPurchaseException() {
        super(ERROR_CODE, "The student already holds every session this monthly purchase would cover");
    }
}
