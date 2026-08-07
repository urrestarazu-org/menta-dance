package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.LoginCommand;
import com.menta.bff.application.dto.TokenPairResponse;

/**
 * Port for communicating with the Auth API.
 * <p>
 * Defines operations needed for authentication flows:
 * - Login (exchange credentials for tokens)
 * - Refresh (exchange refresh token for new access token)
 * - Logout (revoke refresh token)
 * </p>
 * <p>
 * Part of Clean Architecture application layer - abstracts HTTP/REST details.
 * Implementations use WebClient or similar HTTP clients.
 * </p>
 */
public interface AuthApiClient {

    /**
     * Authenticates user credentials and obtains token pair.
     * <p>
     * Calls POST /api/v1/auth/login with email and password.
     * </p>
     *
     * @param command Login credentials (email, password)
     * @return Token pair (access token, refresh token, expiration)
     * @throws AuthenticationException    if credentials are invalid (401)
     * @throws ServiceUnavailableException if Auth API is unreachable (503)
     */
    TokenPairResponse login(LoginCommand command);

    /**
     * Refreshes an expired access token using a refresh token.
     * <p>
     * Calls POST /api/v1/auth/refresh with X-Refresh-Token header.
     * </p>
     *
     * @param refreshToken Refresh token from previous login/refresh
     * @return New token pair (access token, refresh token, expiration)
     * @throws AuthenticationException    if refresh token is invalid/expired (401)
     * @throws RefreshTokenRevokedException if refresh token family was revoked (423)
     * @throws ServiceUnavailableException if Auth API is unreachable (503)
     */
    TokenPairResponse refresh(String refreshToken);

    /**
     * Revokes a refresh token to prevent future use.
     * <p>
     * Calls POST /api/v1/auth/logout with X-Refresh-Token header.
     * Idempotent - revoking an already-revoked token is safe.
     * </p>
     *
     * @param refreshToken Refresh token to revoke
     * @throws ServiceUnavailableException if Auth API is unreachable (503)
     */
    void logout(String refreshToken);

    /**
     * Exception thrown when credentials or refresh token are invalid.
     */
    class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }

        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when refresh token family was revoked (security breach detected).
     */
    class RefreshTokenRevokedException extends RuntimeException {
        public RefreshTokenRevokedException(String message) {
            super(message);
        }

        public RefreshTokenRevokedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when Auth API is unavailable.
     */
    class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }

        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
