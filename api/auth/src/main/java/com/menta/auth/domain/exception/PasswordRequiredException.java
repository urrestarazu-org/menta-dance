package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an account created by an administrator is activated without the password its holder must choose.
 * Only reachable with a valid activation token, so naming the reason reveals nothing to a third party.
 */
public class PasswordRequiredException extends BusinessException {

    private static final String ERROR_CODE = "PASSWORD_REQUIRED";

    public PasswordRequiredException() {
        super(ERROR_CODE, "A password is required to activate this account");
    }
}
