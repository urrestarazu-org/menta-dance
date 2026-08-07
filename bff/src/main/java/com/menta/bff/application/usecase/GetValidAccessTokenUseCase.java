package com.menta.bff.application.usecase;

/**
 * Use case for retrieving a valid access token from session.
 * <p>
 * Orchestrates token retrieval with automatic refresh:
 * 1. Load tokens from session
 * 2. Check if access token is expired
 * 3. If expired, refresh transparently via Auth API
 * 4. Return valid access token
 * </p>
 * <p>
 * This use case enables transparent token refresh before making
 * authenticated API calls.
 * </p>
 * <p>
 * Part of Clean Architecture application layer.
 * </p>
 */
public interface GetValidAccessTokenUseCase {

    /**
     * Gets a valid (non-expired) access token from session.
     * <p>
     * Automatically refreshes if expired.
     * </p>
     *
     * @return Valid access token (JWT)
     * @throws SessionNotFoundException if no active session exists
     * @throws com.menta.bff.application.port.out.AuthApiClient.AuthenticationException if refresh token invalid
     * @throws com.menta.bff.application.port.out.AuthApiClient.RefreshTokenRevokedException if token family revoked (security breach)
     * @throws com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException if Auth API unavailable
     */
    String execute();

    /**
     * Exception thrown when no active session exists.
     */
    class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(String message) {
            super(message);
        }
    }
}
