package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.LoginCommand;
import com.menta.bff.application.dto.RegisterCommand;
import com.menta.bff.application.dto.RequestPasswordResetCommand;
import com.menta.bff.application.dto.ResendActivationCommand;
import com.menta.bff.application.dto.TokenPairResponse;
import java.time.Duration;

/**
 * Port for communicating with the Auth API.
 *
 * <p>Defines operations needed for authentication flows:
 * - Login (exchange credentials for tokens)
 * - Refresh (exchange refresh token for new access token)
 * - Logout (revoke refresh token)
 * - Registration (public account sign-up)
 * - Account activation (token validation and consumption)
 * - Resend of the activation email
 * - Password recovery (request email, then reset with the emailed token)
 * </p>
 *
 * <p>Part of Clean Architecture application layer - abstracts HTTP/REST details.
 * Implementations use WebClient or similar HTTP clients.
 * </p>
 */
public interface AuthApiClient {

    /**
     * Authenticates user credentials and obtains token pair.
     *
     * <p>Calls POST /api/v1/auth/login with email and password.
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
     *
     * <p>Calls POST /api/v1/auth/refresh with X-Refresh-Token header.
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
     *
     * <p>Calls POST /api/v1/auth/logout. The endpoint is authenticated, so the call carries
     * {@code Authorization: Bearer <accessToken>} as well as the {@code X-Refresh-Token}
     * header. Idempotent - revoking an already-revoked token is safe (404 counts as success).
     * </p>
     *
     * <p>Both values are credentials: they must never be logged.
     * </p>
     *
     * @param accessToken  Valid (non-expired) access token of the session
     * @param refreshToken Refresh token to revoke
     * @throws AuthenticationException     if the API rejects the credentials (401/403)
     * @throws ServiceUnavailableException if Auth API is unreachable, degraded (5xx) or
     *                                     answers with an unexpected status
     */
    void logout(String accessToken, String refreshToken);

    /**
     * Submits a public account registration.
     *
     * <p>Calls POST /api/v1/auth/register. The API answers 202 with an empty body for a
     * new account, an already registered email and a disallowed role alike, so success
     * carries no information about the account.
     * </p>
     *
     * @param command Email, password, role and originating client address
     * @throws InvalidRequestException     if the API rejects the input (400)
     * @throws RateLimitedException        if the API rate limit was hit (429)
     * @throws ServiceUnavailableException if Auth API is unreachable or degraded (503)
     */
    void register(RegisterCommand command);

    /**
     * Checks that an activation token is still usable, without consuming it.
     *
     * <p>Calls GET /api/v1/auth/activate/{token}. The token is a credential: it travels in
     * the path of this single call and must never be logged.
     * </p>
     *
     * @param token Opaque activation token received by email
     * @throws InvalidActivationTokenException if the token is invalid, expired or already used (400)
     * @throws RateLimitedException            if the API rate limit was hit (429)
     * @throws ServiceUnavailableException     if Auth API is unreachable or degraded (503)
     */
    void validateActivationToken(String token);

    /**
     * Consumes an activation token and activates its account.
     *
     * <p>Calls POST /api/v1/auth/activate with the token in the JSON body, never in the URL.
     * </p>
     *
     * @param token Opaque activation token received by email
     * @throws InvalidActivationTokenException if the token is invalid, expired or already used (400)
     * @throws RateLimitedException            if the API rate limit was hit (429)
     * @throws ServiceUnavailableException     if Auth API is unreachable or degraded (503)
     */
    void activate(String token);

    /**
     * Asks for a new activation email.
     *
     * <p>Calls POST /api/v1/auth/resend-activation. The API answers 202 with an empty body
     * for an unknown, pending or already active email alike, so success carries no
     * information about the account.
     * </p>
     *
     * @param command Email and originating client address
     * @throws InvalidRequestException     if the API rejects the input (400)
     * @throws RateLimitedException        if the API rate limit was hit (429)
     * @throws ServiceUnavailableException if Auth API is unreachable or degraded (503)
     */
    void resendActivation(ResendActivationCommand command);

    /**
     * Asks for a password recovery email.
     *
     * <p>Calls POST /api/v1/auth/forgot-password. The API answers 202 with an empty body for
     * an unknown, inactive or active email alike, so success carries no information about
     * the account.
     * </p>
     *
     * @param command Email and originating client address
     * @throws InvalidRequestException     if the API rejects the input (400)
     * @throws RateLimitedException        if the API rate limit was hit (429)
     * @throws ServiceUnavailableException if Auth API is unreachable or degraded (503)
     */
    void requestPasswordReset(RequestPasswordResetCommand command);

    /**
     * Consumes a password reset token and sets the new password.
     *
     * <p>Calls POST /api/v1/auth/reset-password with both values in the JSON body, never in
     * the URL. Unlike the other public flows the API tells the causes apart here.
     * </p>
     *
     * @param token       Opaque reset token received by email
     * @param newPassword The new raw password
     * @throws InvalidResetTokenException  if the token is unknown, expired or already used (404/410)
     * @throws PasswordRejectedException   if the password is weak or equal to the current one (400)
     * @throws InvalidRequestException     if the API rejects the input for another reason (400)
     * @throws RateLimitedException        if the API rate limit was hit (429)
     * @throws ServiceUnavailableException if Auth API is unreachable or degraded (503)
     */
    void resetPassword(String token, String newPassword);

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

    /**
     * Exception thrown when the Auth API rejects a request body as invalid (400).
     */
    class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when an activation token is invalid, expired or already used (400).
     * The API does not tell these apart, and neither does this exception: it carries no token.
     */
    class InvalidActivationTokenException extends RuntimeException {
        public InvalidActivationTokenException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when a password reset token is unknown, expired or already used (404/410).
     * It carries no token.
     */
    class InvalidResetTokenException extends RuntimeException {
        public InvalidResetTokenException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when the Auth API refuses the new password (400). It carries no password.
     */
    class PasswordRejectedException extends RuntimeException {
        /** Why the password was refused. */
        public enum Reason { WEAK, SAME_AS_CURRENT }

        private final Reason reason;

        /**
         * Creates the exception.
         *
         * @param reason Why the API refused the password
         */
        public PasswordRejectedException(Reason reason) {
            super("Auth API rejected the new password: " + reason);
            this.reason = reason;
        }

        /**
         * Returns why the password was refused.
         *
         * @return the reason
         */
        public Reason reason() {
            return reason;
        }
    }

    /**
     * Exception thrown when the Auth API rate limit was hit (429).
     */
    class RateLimitedException extends RuntimeException {
        private final transient Duration retryAfter;

        /**
         * Creates the exception.
         *
         * @param message    Description of the failure
         * @param retryAfter Delay announced by the API's Retry-After header, or null when absent
         */
        public RateLimitedException(String message, Duration retryAfter) {
            super(message);
            this.retryAfter = retryAfter;
        }

        /**
         * Returns the delay announced by the API.
         *
         * @return the Retry-After delay, or null when the API sent none
         */
        public Duration retryAfter() {
            return retryAfter;
        }
    }
}
