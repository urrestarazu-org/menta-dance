package com.menta.auth.infrastructure.persistence.entity;

import com.menta.auth.domain.model.RefreshTokenStatus;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA binding for auth_refresh_tokens (ADR-0025 V2 DDL).
 *
 * Persistence model only — never returned across ports. The domain aggregate
 * is com.menta.auth.domain.model.RefreshToken; conversion happens through
 * RefreshTokenJpaMapper so the application layer stays JDBC-free.
 *
 * Column notes:
 *   - PK is BINARY(16) for UUID v4 storage.
 *   - token_hash is CHAR(64) — the SHA-256 hex digest of the opaque refresh.
 *   - status mapped as VARCHAR via EnumType.STRING so adding new statuses
 *     doesn't reshuffle column ordinals.
 */
@Entity
@Table(name = "auth_refresh_tokens")
public class RefreshTokenJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "family_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", length = 64, nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RefreshTokenStatus status;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshTokenJpaEntity() {
        // JPA — required no-arg ctor.
    }

    public RefreshTokenJpaEntity(
        UUID id,
        UUID familyId,
        String tokenHash,
        UUID userId,
        RefreshTokenStatus status,
        long tokenVersion,
        Instant expiresAt,
        Instant createdAt,
        Instant rotatedAt,
        Instant revokedAt
    ) {
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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public void setFamilyId(UUID familyId) {
        this.familyId = familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public RefreshTokenStatus getStatus() {
        return status;
    }

    public void setStatus(RefreshTokenStatus status) {
        this.status = status;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public void setRotatedAt(Instant rotatedAt) {
        this.rotatedAt = rotatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
