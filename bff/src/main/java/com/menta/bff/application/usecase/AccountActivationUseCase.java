package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ActivationRequirement;

/**
 * Use case for activating an account from the link received by email.
 *
 * <p>The token and the password are credentials: implementations never log them and never echo
 * them in a failure.
 * </p>
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface AccountActivationUseCase {

    /**
     * Checks that a token can still activate an account, without consuming it, and what the holder
     * still has to provide.
     *
     * @param token Opaque token taken from the link
     * @return {@link ActivationRequirement#PASSWORD} when the account was created by an administrator and
     *     its holder must choose a password, {@link ActivationRequirement#NONE} otherwise
     * @throws com.menta.bff.application.port.out.AuthApiClient.InvalidActivationTokenException if the token is
     *     malformed, invalid, expired or already used
     * @throws com.menta.bff.application.port.out.AuthApiClient.RateLimitedException if the API rate limit was
     *     hit (429)
     * @throws com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException if the Auth API is
     *     unavailable
     */
    ActivationRequirement checkToken(String token);

    /**
     * Consumes the token and activates its account, which must not need a password.
     *
     * @param token Opaque token taken from the link
     * @throws com.menta.bff.application.port.out.AuthApiClient.InvalidActivationTokenException if the token is
     *     malformed, invalid, expired or already used
     * @throws com.menta.bff.application.port.out.AuthApiClient.PasswordRequiredException if the account was
     *     created by an administrator and its holder must choose a password; the token is not consumed
     * @throws com.menta.bff.application.port.out.AuthApiClient.RateLimitedException if the API rate limit was
     *     hit (429)
     * @throws com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException if the Auth API is
     *     unavailable
     */
    void activate(String token);

    /**
     * Consumes the token and activates its account, setting the password its holder chose.
     *
     * @param token    Opaque token taken from the link
     * @param password The password the holder chose, in raw form
     * @throws com.menta.bff.application.port.out.AuthApiClient.InvalidActivationTokenException if the token is
     *     malformed, invalid, expired or already used
     * @throws com.menta.bff.application.port.out.AuthApiClient.PasswordRejectedException if the password does not
     *     meet the policy; the token is not consumed
     * @throws com.menta.bff.application.port.out.AuthApiClient.RateLimitedException if the API rate limit was
     *     hit (429)
     * @throws com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException if the Auth API is
     *     unavailable
     */
    void activate(String token, String password);
}
