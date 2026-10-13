package com.menta.bff.infrastructure.security;

import org.springframework.security.authentication.AccountStatusException;

/**
 * Raised by the login when the password was right but the account still awaits activation.
 *
 * <p>The login page reads it to offer the way to ask for a new activation email.</p>
 */
public class PendingActivationException extends AccountStatusException {

    public PendingActivationException(String message) {
        super(message);
    }
}
