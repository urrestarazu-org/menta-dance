package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when login credentials are not valid.
 *
 * The error message MUST be identical for "unknown email" and "wrong password"
 * so attackers cannot discriminate between the two cases (spec
 * auth-login: "401 sin discriminar").
 */
public class InvalidCredentialsException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_CREDENTIALS";

    private static final String GENERIC_MESSAGE = "Invalid email or password";

    public InvalidCredentialsException() {
        super(ERROR_CODE, GENERIC_MESSAGE);
    }
}
