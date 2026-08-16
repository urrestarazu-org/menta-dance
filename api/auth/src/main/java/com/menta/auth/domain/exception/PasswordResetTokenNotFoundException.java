package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a reset token's hash has no match at all (US-AUTH-006 escenario
 * 3). Distinct from {@link PasswordResetTokenExpiredException} on purpose: a
 * reset token is a high-entropy secret, not enumerable like an email, so
 * telling the caller "not found" versus "expired" is useful guidance rather
 * than an information leak.
 */
public class PasswordResetTokenNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "PASSWORD_RESET_TOKEN_NOT_FOUND";

    public PasswordResetTokenNotFoundException() {
        super(ERROR_CODE, "Password reset link is invalid");
    }
}
