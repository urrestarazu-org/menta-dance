package com.menta.auth.application.port.in;

import com.menta.auth.application.dto.RefreshCommand;
import com.menta.auth.application.dto.TokenPair;

/**
 * Application-layer entry point for the refresh-rotation flow (auth-refresh spec).
 *
 * Side-effects in the same transaction (ADR-0027):
 * - Refresh marked USED on rotation.
 * - New refresh row inserted in the same family.
 * - Outbox RefreshRotated appended with new refresh id as aggregate_id.
 *
 * On compromise (presented in USED, with stale token_version, or unknown hash):
 * - User token_version bumped (only when stale was caused by the current event, not
 *   when user is already higher).
 * - Family revoked via RefreshTokenRepository#revokeFamily.
 * - Outbox RefreshRevoked appended.
 *
 * Expiry and REVOKED presentations: refuse without state change.
 * Reconciler degraded: throw AuthDegradedException (controller maps to 503).
 */
public interface RefreshTokenUseCase {

    TokenPair execute(RefreshCommand command);
}
