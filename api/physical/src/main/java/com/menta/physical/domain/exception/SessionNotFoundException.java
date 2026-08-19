package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/** Thrown when a {@code sessionId} does not resolve to an existing session (US-PHYSICAL-006). */
public class SessionNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "SESSION_NOT_FOUND";

    public SessionNotFoundException() {
        super(ERROR_CODE, "Session not found");
    }
}
