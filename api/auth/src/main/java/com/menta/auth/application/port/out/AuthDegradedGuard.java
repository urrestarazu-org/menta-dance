package com.menta.auth.application.port.out;

/**
 * Cross-module port to a fail-closed health guard that signals whether the
 * outbox reconciler is degraded (ADR-0026 AUTH_DEGRADED).
 *
 * When isDegraded() returns true, LoginUseCase / RefreshTokenUseCase MUST
 * refuse to issue or rotate tokens and surface a domain signal (mapped to
 * HTTP 503 + Retry-After: 30 by the controller).
 */
public interface AuthDegradedGuard {

    /**
     * @return true when the reconciler has been lagging longer than the
     *         configured window (default 30 s); false when the system is healthy.
     */
    boolean isDegraded();
}
