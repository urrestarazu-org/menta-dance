package com.menta.auth.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Refresh token aggregate (ADR-0025).
 *
 * Pure POJO without framework annotations. MySQL is the authority for the
 * status, family scope, and token_version snapshot. The corresponding JPA
 * entity lives in :api:auth infrastructure layer (PR2).
 */
public class RefreshToken {

    private final UUID id;
    private final UUID familyId;
    private final String tokenHash;
    private final UserId userId;
    private RefreshTokenStatus status;
    private final long tokenVersion;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant rotatedAt;
    private Instant revokedAt;

    private RefreshToken(
        UUID id,
        UUID familyId,
        String tokenHash,
        UserId userId,
        RefreshTokenStatus status,
        long tokenVersion,
        Instant expiresAt,
        Instant createdAt,
        Instant rotatedAt,
        Instant revokedAt
    ) {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (familyId == null) {
            throw new IllegalArgumentException("familyId cannot be null");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash cannot be null or empty");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt cannot be null");
        }
        this.id = id;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.status = status;
        this.tokenVersion = tokenVersion;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.rotatedAt = rotatedAt;
        this.revokedAt = revokedAt;
    }

    /**
     * Factory: brand new refresh in a NEW family. Invariant: status=ACTIVE,
     * rotatedAt=null, revokedAt=null. Used on initial login.
     */
    public static RefreshToken newFamily(
        UserId userId,
        String tokenHash,
        long tokenVersion,
        Instant expiresAt
    ) {
        Instant now = Instant.now();
        return new RefreshToken(
            UUID.randomUUID(),
            UUID.randomUUID(),
            tokenHash,
            userId,
            RefreshTokenStatus.ACTIVE,
            tokenVersion,
            expiresAt,
            now,
            null,
            null
        );
    }

    /**
     * Factory: rotate within an existing family. Invariant: status=ACTIVE,
     * rotatedAt=null, revokedAt=null. Used by RefreshTokenUseCase on
     * successful rotation.
     */
    public static RefreshToken rotate(
        UserId userId,
        UUID familyId,
        String tokenHash,
        long tokenVersion,
        Instant expiresAt
    ) {
        Instant now = Instant.now();
        return new RefreshToken(
            UUID.randomUUID(),
            familyId,
            tokenHash,
            userId,
            RefreshTokenStatus.ACTIVE,
            tokenVersion,
            expiresAt,
            now,
            null,
            null
        );
    }

    /**
     * Behavior: ACTIVE -> USED. Idempotent guard: only succeeds from ACTIVE.
     * After this call the token is compromised-tokens-present; any future
     * presentation triggers family revocation.
     */
    public void markUsed() {
        if (this.status != RefreshTokenStatus.ACTIVE) {
            throw new IllegalStateException(
                "RefreshToken can only be marked USED from ACTIVE; current=" + this.status
            );
        }
        this.status = RefreshTokenStatus.USED;
        this.rotatedAt = Instant.now();
    }

    /**
     * Behavior: mark REVOKED. Idempotent — calling repeatedly is safe and
     * preserves the original revokedAt timestamp.
     */
    public void markRevoked() {
        if (this.status == RefreshTokenStatus.REVOKED) {
            return;
        }
        this.status = RefreshTokenStatus.REVOKED;
        this.revokedAt = Instant.now();
    }

    public boolean isRevoked() {
        return this.status == RefreshTokenStatus.REVOKED;
    }

    /**
     * USED or REVOKED indicates the refresh has been consumed; presenting it
     * again must trigger family revocation. ROTATED is treated as USED.
     */
    public boolean isCompromised() {
        return this.status == RefreshTokenStatus.USED
            || this.status == RefreshTokenStatus.REVOKED;
    }

    public boolean isExpired(Instant now) {
        return !this.expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UserId getUserId() {
        return userId;
    }

    public RefreshTokenStatus getStatus() {
        return status;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RefreshToken that = (RefreshToken) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RefreshToken{id=" + id + ", familyId=" + familyId
            + ", status=" + status + ", tokenVersion=" + tokenVersion
            + ", expiresAt=" + expiresAt + "}";
    }
}
