package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a check-in QR is issued or redeemed for a session whose
 * {@link com.menta.physical.domain.model.SessionStatus} is {@code
 * CANCELLED} (US-PHYSICAL-001; invariant documented in
 * {@code docs/diagrams/STATE-DIAGRAMS.md}: "CANCELLED bloquea check-ins
 * aunque existan asignaciones").
 *
 * <p>A cancelled session can still have confirmed assignments on record —
 * cancellation does not retroactively delete them — so this check must run
 * before the assignment lookup would otherwise let a stale assignment
 * grant access to a class that no longer happens.</p>
 */
public class SessionCancelledException extends BusinessException {

    private static final String ERROR_CODE = "SESSION_CANCELLED";

    public SessionCancelledException() {
        super(ERROR_CODE, "This session has been cancelled and no longer accepts check-ins.");
    }
}
