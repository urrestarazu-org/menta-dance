package com.menta.bff.application.dto;

import java.util.Objects;

/**
 * DTO for Auth API token pair response.
 * <p>
 * Represents the result of login or refresh operations from Auth API.
 * Contains access token, refresh token, and TTL (time-to-live) in seconds.
 * </p>
 * <p>
 * SECURITY: Never log token fields.
 * </p>
 *
 * @param accessToken  JWT access token for API authentication
 * @param refreshToken Opaque refresh token for obtaining new access tokens
 * @param expiresIn    TTL in seconds until access token expires (e.g., 900 for 15 min)
 */
public record TokenPairResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {
    /**
     * Compact constructor with validation.
     */
    public TokenPairResponse {
        Objects.requireNonNull(accessToken, "accessToken cannot be null");
        Objects.requireNonNull(refreshToken, "refreshToken cannot be null");

        if (expiresIn <= 0) {
            throw new IllegalArgumentException("expiresIn must be positive, got: " + expiresIn);
        }
    }

    /**
     * Custom toString that prevents token leakage in logs.
     *
     * @return String representation without sensitive token values
     */
    @Override
    public String toString() {
        return "TokenPairResponse[expiresIn=" + expiresIn + "s]";
    }
}
