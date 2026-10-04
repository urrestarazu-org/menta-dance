package com.menta.bff.application.dto;

import java.util.Objects;

/**
 * Outcome of a successful login.
 *
 * @param role role name read from the access token (e.g. {@code STUDENT})
 */
public record LoginResult(String role) {

    /**
     * Compact constructor with validation.
     */
    public LoginResult {
        Objects.requireNonNull(role, "role cannot be null");
    }
}
