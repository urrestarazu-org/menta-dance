package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a reset token exists but is no longer usable because it expired
 * or was superseded by a newer request (US-AUTH-005 escenario 3 /
 * US-AUTH-006 escenario 2).
 *
 * <p>Deliberately covers both {@code EXPIRED} and {@code INVALIDATED}: telling
 * an invalidated (superseded) token's holder "this was already used" would be
 * false — they never used it, a newer request replaced it. "Expired, request a
 * new one" is the honest, actionable message for both cases; the remedy is
 * identical either way.</p>
 */
public class PasswordResetTokenExpiredException extends BusinessException {

    private static final String ERROR_CODE = "PASSWORD_RESET_TOKEN_EXPIRED";

    public PasswordResetTokenExpiredException() {
        super(ERROR_CODE, "Password reset link has expired");
    }
}
