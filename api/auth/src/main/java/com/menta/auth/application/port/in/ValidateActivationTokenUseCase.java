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
     * @throws com.menta.auth.domain.exception.ActivationTokenInvalidException when the token is unknown, not active
     *      or its account is no longer pending activation
     */
    void validate(ActivateAccountCommand command);
}
