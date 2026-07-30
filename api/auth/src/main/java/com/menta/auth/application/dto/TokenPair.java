package com.menta.auth.application.dto;

import java.time.Duration;

/**
 * Token-pair result returned by Login / Refresh use cases.
 *
 * Mirrors the spec response shape (POST /auth/login 200 → access_token,
 * refresh_token, token_type, expires_in) without leaking the JWT wire format
 * upward.
 */
public record TokenPair(
    String accessToken,
    String refreshToken,
    String tokenType,
    Duration expiresIn
) {
    public static final String TOKEN_TYPE_BEARER = "Bearer";
}
