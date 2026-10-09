package com.menta.bff.application.usecase;

/**
 * Use case for activating an account from the link received by email.
 *
 * <p>The token is a credential: implementations never log it and never echo it in a
 * failure.
 * </p>
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface AccountActivationUseCase {

    /**
     * Checks that a token can still activate an account, without consuming it.
     *
     * @param token Opaque token taken from the link
     * @return false when the token is malformed, invalid, expired or already used
     * @throws com.menta.bff.application.port.out.AuthApiClient.RateLimitedException if the API rate limit was
     *     hit (429)
     * @throws com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException if the Auth API is
     *     unavailable
     */
    boolean isTokenValid(String token);

    /**
     * Consumes the token and activates its account.
     *
     * @param token Opaque token taken from the link
     * @throws com.menta.bff.application.port.out.AuthApiClient.InvalidActivationTokenException if the token is
     *     malformed, invalid, expired or already used
     * @throws com.menta.bff.application.port.out.AuthApiClient.RateLimitedException if the API rate limit was
     *     hit (429)
     * @throws com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException if the Auth API is
     *     unavailable
     */
    void activate(String token);
}
