package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.ActivateAccountCommand;

/**
 * Application-layer entry point for validating an account activation token
 * without consuming it or changing the associated user.
 */
public interface ValidateActivationTokenUseCase {

    void validate(ActivateAccountCommand command);
}
