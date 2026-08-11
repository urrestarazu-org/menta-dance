package com.menta.bff.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Value object representing authentication tokens stored in server-side session.
 * <p>
 * Contains JWT access token, opaque refresh token, and expiration timestamp.
 * This class is part of the domain layer and has ZERO framework dependencies
 * (no Spring, no JPA, no Jackson annotations).
 * </p>
 * <p>
 * Implements {@link Serializable} for Redis session storage via Spring Session.
 * </p>
 *
 * @param accessToken  JWT access token for API authentication (short-lived, 15 min)
 * @param refreshToken Opaque refresh token for obtaining new access tokens (long-lived, 7 days)
 * @param expiresAt    Instant when the access token expires (not the refresh token)
 */
public record SessionTokens(
        String accessToken,
        String refreshToken,
        Instant expiresAt
) implements Serializable {
    /**
     * Compact constructor with validation.
     * Ensures all fields are non-null per Clean Architecture domain rules.
     */
    public SessionTokens {
        Objects.requireNonNull(accessToken, "accessToken cannot be null");
        Objects.requireNonNull(refreshToken, "refreshToken cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    }

    /**
     * Checks if the access token is expired at the given time.
     *
     * @param now Current time to compare against expiration
     * @return true if the access token is expired (now is after expiresAt)
     */
    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        return now.isAfter(expiresAt);
    }

    /**
     * Custom toString that prevents token leakage in logs.
     * <p>
     * SECURITY: Never log raw tokens - they are credentials.
     * Only log expiration time for debugging.
     * </p>
     *
     * @return String representation without sensitive token values
     */
    @Override
    public String toString() {
        return "SessionTokens[expiresAt=" + expiresAt + "]";
    }
}
