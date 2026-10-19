package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a MANUAL check-in (#45, US-PHYSICAL-008) is attempted against a
 * session whose {@link com.menta.physical.domain.model.SessionStatus} is
 * {@code CANCELLED}. Unlike the QR flow's {@link SessionCancelledException},
 * this is the only session-state gate the MANUAL path consults: an
 * already-elapsed but non-cancelled session is deliberately still accepted
 * (retroactive backfill, D8) — {@code hasOccurred} is never checked here.
 */
public class SessionNotActiveException extends BusinessException {

    private static final String ERROR_CODE = "SESSION_NOT_ACTIVE";

    public SessionNotActiveException() {
        super(ERROR_CODE, "This session is cancelled and no longer accepts check-ins.");
    }
}
