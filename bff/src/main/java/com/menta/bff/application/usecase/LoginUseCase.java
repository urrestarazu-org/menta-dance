package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.LoginCommand;
import com.menta.bff.application.dto.LoginResult;

/**
 * Use case for user login flow.
 *
 * <p>Orchestrates the login process:
 * 1. Call Auth API with credentials
 * 2. Store received tokens in server-side session
 * 3. Return the user's role (tokens never exposed to browser)
 * </p>
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface LoginUseCase {

    /**
     * Executes login flow.
     *
     * <p>Calls Auth API to authenticate credentials, then stores
     * the returned token pair in the current HTTP session. The role is read
     * from the access token before anything is stored, so a token without a
     * usable role never reaches the session.
     * </p>
     *
     * @param command Login credentials (email, password)
     * @return the authenticated user's role
     * @throws com.menta.bff.application.port.out.AuthApiClient.AuthenticationException if credentials invalid (401)
     * @throws com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException if Auth API unavailable
     *     (503)
     * @throws com.menta.bff.application.port.out.AccessTokenClaimsReader.UnreadableTokenException if the access
     *     token carries no valid role
     * @throws IllegalStateException if no active HTTP session
     */
    LoginResult execute(LoginCommand command);
}
