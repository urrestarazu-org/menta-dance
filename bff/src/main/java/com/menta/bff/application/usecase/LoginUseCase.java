package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.LoginCommand;

/**
 * Use case for user login flow.
 * <p>
 * Orchestrates the login process:
 * 1. Call Auth API with credentials
 * 2. Store received tokens in server-side session
 * 3. Return success (tokens never exposed to browser)
 * </p>
 * <p>
 * Part of Clean Architecture application layer.
 * </p>
 */
public interface LoginUseCase {

    /**
     * Executes login flow.
     * <p>
     * Calls Auth API to authenticate credentials, then stores
     * the returned token pair in the current HTTP session.
     * </p>
     *
     * @param command Login credentials (email, password)
     * @throws com.menta.bff.application.port.out.AuthApiClient.AuthenticationException if credentials invalid (401)
     * @throws com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException if Auth API unavailable (503)
     * @throws IllegalStateException if no active HTTP session
     */
    void execute(LoginCommand command);
}
