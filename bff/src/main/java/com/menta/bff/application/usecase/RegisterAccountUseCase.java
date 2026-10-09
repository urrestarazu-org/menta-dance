package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.RegisterCommand;

/**
 * Use case for the public account registration.
 *
 * <p>The Auth API answers the same way whether the email was new, already
 * registered or the role was not allowed, so this use case has no result: the
 * caller cannot learn anything about the account from it.
 * </p>
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface RegisterAccountUseCase {

    /**
     * Submits a registration to the Auth API.
     *
     * @param command Email, password and role of the new account
     * @throws com.menta.bff.application.port.out.AuthApiClient.InvalidRequestException if the API rejects the
     *     input (400)
     * @throws com.menta.bff.application.port.out.AuthApiClient.RateLimitedException if the API rate limit was
     *     hit (429)
     * @throws com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException if the Auth API is
     *     unavailable
     */
    void execute(RegisterCommand command);
}
