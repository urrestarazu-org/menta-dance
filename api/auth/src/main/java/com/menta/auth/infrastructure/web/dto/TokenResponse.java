package com.menta.auth.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;

/**
 * TokenResponse wire shape for /api/v1/auth/login and /api/v1/auth/refresh 200 OK
 * responses (spec auth-login: 200 → access_token, token_type, expires_in).
 *
 * <p>The refresh_token is returned in the X-Refresh-Token HTTP header for
 * security reasons (not exposed in response body).
 *
 * <p>The Java field names stay semantic (camelCase, Duration). Jackson's @JsonProperty
 * maps them to the spec-required snake_case JSON so the
 * application layer never leaks wire-format concerns.
 */
public record TokenResponse(
    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("token_type")
    String tokenType,

    // Spring Boot disables WRITE_DURATIONS_AS_TIMESTAMPS, which would emit "PT15M";
    // the shape is pinned here so the wire contract does not depend on mapper defaults.
    @JsonProperty("expires_in")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
    Duration expiresIn
) {
}
