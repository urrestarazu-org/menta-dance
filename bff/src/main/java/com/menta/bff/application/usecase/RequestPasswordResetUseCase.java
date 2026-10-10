package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.RequestPasswordResetCommand;

/**
 * Use case for asking a password recovery email.
 *
 * <p>The Auth API answers the same way whether the account exists or not, so this use case
 * has no result: the caller cannot learn anything about the account from it.
 * </p>
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface RequestPasswordResetUseCase {

    /**
     * Asks the Auth API to send the recovery email.
     *
     * @param command Email and originating client address
     * @throws com.menta.bff.application.port.out.AuthApiClient.InvalidRequestException if the API rejects the
     *     input (400)
     * @throws com.menta.bff.application.port.out.AuthApiClient.RateLimitedException if the API rate limit was
     *     hit (429)
     * @throws com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException if the Auth API is
     *     unavailable
     */
    void execute(RequestPasswordResetCommand command);
}
