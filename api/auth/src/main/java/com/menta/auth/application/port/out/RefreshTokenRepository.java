package com.menta.auth.application.port.out;

import com.menta.auth.domain.model.RefreshToken;
import com.menta.auth.domain.model.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the refresh-token aggregate.
 *
 * Operations:
 * - findByHash: lookup by SHA-256 hex token_hash (raw refresh never leaves the
 *   client boundary).
 * - save: insert a new refresh (either initial family or rotation).
 * - revokeFamily: bulk-revoke every ACTIVE/USED token in a family. Idempotent.
 *
 * All operations MUST participate in the caller's transaction so refresh
 * lifecycle and outbox append share an atomic COMMIT (ADR-0027).
 */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByHash(String tokenHash);

    RefreshToken save(RefreshToken newRefresh);

    /**
     * Bulk revoke every refresh in the given family whose status is not
     * already REVOKED. Idempotent — calling twice has the same effect as
     * calling once.
     */
    void revokeFamily(UUID familyId);

    /**
     * Returns the active entries in a family — used by the ROTATED/USED
     * detection path to enumerate siblings before bulk-revoke.
     */
    List<RefreshToken> findActiveOrUsedByFamily(UUID familyId);

    /**
     * Bulk revoke every refresh across every family owned by this user, not
     * just one family (US-AUTH-006: a password reset must close every
     * session, on every device — {@link #revokeFamily} only reaches the one
     * family a compromised token belongs to). Idempotent.
     */
    void revokeAllByUserId(UserId userId);
}
