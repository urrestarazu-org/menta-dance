package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.LogoutCommand;

/**
 * Application-layer entry point for the logout flow (auth-login spec).
 *
 * <p>Side-effects in the SAME transaction (ADR-0027):
 * - Refresh marked REVOKED (immutable thereafter).
 * - Outbox UserLoggedOut row appended.
 *
 * <p>If the presented refresh is in USED state (already rotated, presented via
 * compromised path) the use case MUST escalate to family revocation and
 * bump the user's token_version, then rethrow RefreshTokenCompromisedException
 * (auth-login spec: "Refresh ya rotado activa revocación de familia").
 *
 * <p>No return value: HTTP layer maps a successful logout to 204 No Content.
 */
public interface LogoutUseCase {

    /**
     * Ends the session identified by the presented refresh token.
     *
     * @param command carries the raw refresh token presented by the client
     * @throws com.menta.auth.domain.exception.RefreshTokenCompromisedException when the token is unknown, was already
     *      revoked, or was already rotated (in which case its whole family is revoked first)
     */
    void execute(LogoutCommand command);
}
