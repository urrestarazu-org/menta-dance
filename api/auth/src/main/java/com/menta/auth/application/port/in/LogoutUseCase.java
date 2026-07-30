package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.LogoutCommand;

/**
 * Application-layer entry point for the logout flow (auth-login spec).
 *
 * Side-effects in the SAME transaction (ADR-0027):
 * - Refresh marked REVOKED (immutable thereafter).
 * - Outbox UserLoggedOut row appended.
 *
 * If the presented refresh is in USED state (already rotated, presented via
 * compromised path) the use case MUST escalate to family revocation and
 * bump the user's token_version, then rethrow RefreshTokenCompromisedException
 * (auth-login spec: "Refresh ya rotado activa revocación de familia").
 *
 * No return value: HTTP layer maps a successful logout to 204 No Content.
 */
public interface LogoutUseCase {

    void execute(LogoutCommand command);
}
