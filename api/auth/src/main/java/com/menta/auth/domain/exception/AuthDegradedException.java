package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when the outbox reconciler reports the system as degraded
 * (ADR-0026 AUTH_DEGRADED). Login, refresh and authenticated requests all
 * refuse to advance while degraded; the HTTP layer maps this to 503 with
 * Retry-After: 30.
 *
 * Internal users of this exception:
 * - LoginUseCase / RefreshTokenUseCase / LogoutUseCase guard against issuing
 *   or rotating tokens while the guard reports degraded.
 */
public class AuthDegradedException extends BusinessException {

    private static final String ERROR_CODE = "AUTH_DEGRADED";

    public AuthDegradedException() {
        super(ERROR_CODE, "Authentication is temporarily unavailable; retry after 30s");
    }
}
