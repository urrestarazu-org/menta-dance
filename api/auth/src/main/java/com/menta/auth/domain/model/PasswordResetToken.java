package com.menta.auth.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Single-use password reset credential (US-AUTH-005 / US-AUTH-006).
 *
 * <p>The raw credential never enters this aggregate. Only its SHA-256 digest is
 * retained, so a domain object cannot accidentally expose the secret that grants
 * a password change — the plaintext exists solely inside the email link.</p>
 *
 * <p>Mirrors {@link ActivationToken} in shape, but keeps its terminal states
 * meaningful to the caller: expired, used and superseded map to different
 * responses, because a reset token is high-entropy and not enumerable, so
 * telling the user which one occurred is guidance rather than a leak.</p>
 */
public final class PasswordResetToken {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final UUID id;
    private final UserId userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant usedAt;
    private Instant invalidatedAt;

    private PasswordResetToken(
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

    public static PasswordResetToken issue(
        UserId userId,
        String tokenHash,
        Instant expiresAt,
        Instant now
    ) {
        return new PasswordResetToken(
            UUID.randomUUID(), userId, tokenHash, expiresAt, now, null, null
        );
    }

    public static PasswordResetToken reconstitute(
        UUID id,
        UserId userId,
        String tokenHash,
        Instant expiresAt,
        Instant createdAt,
        Instant usedAt,
        Instant invalidatedAt
    ) {
        return new PasswordResetToken(
            id, userId, tokenHash, expiresAt, createdAt, usedAt, invalidatedAt
        );
    }

    public PasswordResetTokenStatus statusAt(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        if (usedAt != null) {
            return PasswordResetTokenStatus.USED;
        }
        if (invalidatedAt != null) {
            return PasswordResetTokenStatus.INVALIDATED;
        }
        if (!expiresAt.isAfter(now)) {
            return PasswordResetTokenStatus.EXPIRED;
        }
        return PasswordResetTokenStatus.ACTIVE;
    }

    /** Marks the credential as spent. Single use is enforced here, not by callers. */
    public void consume(Instant now) {
        requireActive(now);
        usedAt = now;
    }

    /** Supersedes the credential because a newer reset was requested. */
    public void invalidate(Instant now) {
        requireActive(now);
        invalidatedAt = now;
    }

    private void requireActive(Instant now) {
        PasswordResetTokenStatus current = statusAt(now);
        if (current != PasswordResetTokenStatus.ACTIVE) {
            throw new IllegalStateException(
                "Password reset token must be active; current=" + current
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
        if (!(other instanceof PasswordResetToken token)) {
            return false;
        }
        return id.equals(token.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** Deliberately omits the digest: it is the lookup key for a password change. */
    @Override
    public String toString() {
        return "PasswordResetToken[id=" + id + ", userId=" + userId + "]";
    }
}
