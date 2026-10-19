package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.ActivateAccountCommand;

/**
 * Application-layer entry point for the account activation flow
 * (auth-account-activation spec: "Activación de un solo uso").
 *
 * <p>On success the associated {@code ActivationToken} is consumed and the
 * {@code User} transitions to {@code ACTIVE} in the same transaction. Any
 * failure — token not found, expired, already used, already invalidated, or
 * a racing activation — raises the single generic
 * {@link com.menta.auth.domain.exception.ActivationTokenInvalidException} so
 * callers cannot distinguish the reason.</p>
 */
public interface ActivateAccountUseCase {

    /**
     * Activates the account that owns the presented raw activation token.
     *
     * @param command carries the raw token from the activation link
     * @throws com.menta.auth.domain.exception.ActivationTokenInvalidException for every failure, without telling
     *      the caller whether the token was unknown, expired, used, invalidated or lost a race
     */
    void activate(ActivateAccountCommand command);
}
