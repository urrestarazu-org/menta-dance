package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.ActivateAccountCommand;

/**
 * Application-layer entry point for validating an account activation token
 * without consuming it or changing the associated user.
 */
public interface ValidateActivationTokenUseCase {

    /**
     * Checks that the token is active and belongs to a pending account, without consuming it.
     *
     * @param command carries the raw token from the activation link
     * @return whether the holder must choose a password when activating, because an administrator created the
     *      account; {@code false} for accounts that registered themselves with a password
     * @throws com.menta.auth.domain.exception.ActivationTokenInvalidException when the token is unknown, not active
     *      or its account is no longer pending activation
     */
    boolean validate(ActivateAccountCommand command);
}
