package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ResendActivationCommand;

/**
 * Use case for asking a new activation email.
 *
 * <p>The Auth API answers the same way whether the email is unknown, pending or already
 * active, so this use case has no result: the caller cannot learn anything about the
 * account from it.
 * </p>
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface ResendActivationUseCase {

    /**
     * Asks the Auth API to resend the activation email.
     *
     * @param command Email and originating client address
     * @throws com.menta.bff.application.port.out.AuthApiClient.InvalidRequestException if the API rejects the
     *     input (400)
     * @throws com.menta.bff.application.port.out.AuthApiClient.RateLimitedException if the API rate limit was
     *     hit (429)
     * @throws com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException if the Auth API is
     *     unavailable
     */
    void execute(ResendActivationCommand command);
}
