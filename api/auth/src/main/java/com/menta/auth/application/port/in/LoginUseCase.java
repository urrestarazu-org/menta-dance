package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.LoginCommand;
import com.menta.auth.application.dto.TokenPair;

/**
 * Application-layer entry point for the login flow (auth-login spec).
 *
 * <p>Contract side-effects after commit (within the caller's transaction):
 * - Refresh token persisted in ACTIVE state in a new family (auth_refresh_tokens).
 * - Outbox AuthUserLoggedIn row appended with aggregate_id=jti and
 *   payload={token_version}.
 *
 * <p>Failure outcomes each map to a specific exception type so the controller
 * can render them without leaking information:
 * - InvalidCredentialsException: unknown email OR wrong password (no discrimination).
 * - LockedUserException: user status is LOCKED.
 * - AuthDegradedException: outbox reconciler reports degraded.
 */
public interface LoginUseCase {

    /**
     * Authenticates the credentials and issues a new access/refresh token pair.
     *
     * <p>Each successful login starts a new refresh-token family; it never reuses an existing one.
     *
     * @param command carries the email, the password and the client fingerprint used for throttling
     * @return the access token and the refresh token to hand to the client
     */
    TokenPair execute(LoginCommand command);
}
