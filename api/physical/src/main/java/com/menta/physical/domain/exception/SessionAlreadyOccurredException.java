package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when any modification (reschedule, capacity, notes, cancellation)
 * is attempted on a session whose {@code scheduledAt} has already passed
 * (US-PHYSICAL-006 escenario 7).
 */
public class SessionAlreadyOccurredException extends BusinessException {

    private static final String ERROR_CODE = "SESSION_ALREADY_OCCURRED";

    public SessionAlreadyOccurredException() {
        super(ERROR_CODE, "Session has already occurred and cannot be modified");
    }
}
