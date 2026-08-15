package com.menta.auth.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Single-use account activation credential.
 *
 * <p>The raw credential never enters this aggregate. Only its SHA-256 digest is
 * retained so domain objects cannot accidentally expose the secret.</p>
 */
public final class ActivationToken {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final UUID id;
    private final UserId userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant usedAt;
    private Instant invalidatedAt;

    private ActivationToken(
        UUID id,
        UserId userId,
        String tokenHash,
        Instant expiresAt,
        Instant createdAt,
        Instant usedAt,
        Instant invalidatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        if (tokenHash == null || !SHA_256_HEX.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("tokenHash must be a lowercase SHA-256 hex digest");
        }
        this.tokenHash = tokenHash;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (usedAt != null && invalidatedAt != null) {
            throw new IllegalArgumentException("token cannot be both used and invalidated");
        }
        this.usedAt = usedAt;
        this.invalidatedAt = invalidatedAt;
    }

    public static ActivationToken issue(
        UserId userId,
        String tokenHash,
        Instant expiresAt,
        Instant now
    ) {
        return new ActivationToken(
            UUID.randomUUID(), userId, tokenHash, expiresAt, now, null, null
        );
    }

    public static ActivationToken reconstitute(
        UUID id,
        UserId userId,
        String tokenHash,
        Instant expiresAt,
        Instant createdAt,
        Instant usedAt,
        Instant invalidatedAt
    ) {
        return new ActivationToken(
            id, userId, tokenHash, expiresAt, createdAt, usedAt, invalidatedAt
        );
    }

    public ActivationTokenStatus statusAt(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        if (usedAt != null) {
            return ActivationTokenStatus.USED;
        }
        if (invalidatedAt != null) {
            return ActivationTokenStatus.INVALIDATED;
        }
        if (!expiresAt.isAfter(now)) {
            return ActivationTokenStatus.EXPIRED;
        }
        return ActivationTokenStatus.ACTIVE;
    }

    public void consume(Instant now) {
        requireActive(now);
        usedAt = now;
    }

    public void invalidate(Instant now) {
        requireActive(now);
        invalidatedAt = now;
    }

    private void requireActive(Instant now) {
        ActivationTokenStatus current = statusAt(now);
        if (current != ActivationTokenStatus.ACTIVE) {
            throw new IllegalStateException(
                "Activation token must be active; current=" + current
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ActivationToken that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ActivationToken{id=" + id + ", userId=" + userId
            + ", expiresAt=" + expiresAt + ", usedAt=" + usedAt
            + ", invalidatedAt=" + invalidatedAt + "}";
    }
}
