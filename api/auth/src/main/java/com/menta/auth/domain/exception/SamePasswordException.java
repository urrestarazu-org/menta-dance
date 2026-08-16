package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a password reset attempts to set the same password the account
 * already has (US-AUTH-006 escenario 6).
 */
public class SamePasswordException extends BusinessException {

    private static final String ERROR_CODE = "SAME_PASSWORD";

    public SamePasswordException() {
        super(ERROR_CODE, "The new password must be different from the current one");
    }
}
