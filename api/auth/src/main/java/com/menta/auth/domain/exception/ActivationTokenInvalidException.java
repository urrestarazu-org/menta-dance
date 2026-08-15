package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an activation token cannot activate an account: not found,
 * expired, already used, or already invalidated. A single generic exception
 * type and message covers every reason so a caller cannot distinguish which
 * one occurred over the wire (auth-account-activation spec: "Reutilización" /
 * "Expiración o invalidación" — mirrors {@link InvalidCredentialsException}'s
 * non-discriminating shape for login).
 */
public class ActivationTokenInvalidException extends BusinessException {

    private static final String ERROR_CODE = "ACTIVATION_TOKEN_INVALID";

    private static final String GENERIC_MESSAGE = "Activation token is invalid or expired";

    public ActivationTokenInvalidException() {
        super(ERROR_CODE, GENERIC_MESSAGE);
    }
}
