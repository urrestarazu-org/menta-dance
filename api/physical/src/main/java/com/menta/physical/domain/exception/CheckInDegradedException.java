package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when the check-in Redis lock cannot be evaluated because Redis
 * itself is unreachable (US-PHYSICAL-001 escenario 6). Mirrors {@code
 * AuthDegradedException}: the HTTP layer maps this to 503 with a
 * {@code Retry-After} header.
 *
 * <p>Fail-closed by design. A door reader flood while Redis is down must
 * never silently fall through to "no lock, insert away" — that would
 * reopen the double-insert race the lock exists to prevent, right when the
 * system is least able to reason about it. Rejecting every scan until
 * Redis recovers is the safer failure mode; the schema's UNIQUE
 * {@code (session_id, user_id)} constraint remains the last line of
 * defense, but this exception keeps it from being the only one.</p>
 */
public class CheckInDegradedException extends BusinessException {

    private static final String ERROR_CODE = "CHECK_IN_DEGRADED";

    public CheckInDegradedException() {
        super(ERROR_CODE, "Check-in is temporarily unavailable; retry after 30s");
    }
}
