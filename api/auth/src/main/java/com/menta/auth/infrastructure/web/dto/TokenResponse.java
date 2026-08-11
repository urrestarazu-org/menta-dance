package com.menta.auth.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;

/**
 * TokenResponse wire shape for /api/v1/auth/login and /api/v1/auth/refresh 200 OK
 * responses (spec auth-login: 200 → access_token, token_type, expires_in).
 *
 * The refresh_token is returned in the X-Refresh-Token HTTP header for
 * security reasons (not exposed in response body).
 *
 * The Java field names stay semantic (camelCase, Duration). Jackson's
 * @JsonProperty maps them to the spec-required snake_case JSON so the
 * application layer never leaks wire-format concerns.
 */
public record TokenResponse(
    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("token_type")
    String tokenType,

    @JsonProperty("expires_in")
    Duration expiresIn
) {
}
