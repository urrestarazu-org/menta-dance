package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a reset token was already consumed by a prior successful reset
 * (US-AUTH-006 escenario 4). Kept distinct from
 * {@link PasswordResetTokenExpiredException} so the message stays truthful:
 * "already used" only fires when the token really was.
 */
public class PasswordResetTokenAlreadyUsedException extends BusinessException {

    private static final String ERROR_CODE = "PASSWORD_RESET_TOKEN_ALREADY_USED";

    public PasswordResetTokenAlreadyUsedException() {
        super(ERROR_CODE, "Password reset link was already used");
    }
}
