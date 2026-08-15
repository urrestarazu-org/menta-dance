package com.menta.bff.application.dto;

import java.util.Objects;

/**
 * Command DTO for user login request.
 * <p>
 * Captures user credentials from login form.
 * Part of application layer - bridges controller input to use case.
 * </p>
 * <p>
 * SECURITY: Never log password field.
 * </p>
 *
 * @param email    User email address (username)
 * @param password User password (plain text, will be sent over HTTPS)
 * @param clientAddress Canonical originating address of the client, resolved
 *     from the trusted proxy hop (ADR-0035). Nullable: when it cannot be
 *     established the Auth API falls back to the peer address it observes,
 *     which is the pre-existing behaviour and never less safe than trusting an
 *     unverified value.
 */
public record LoginCommand(
        String email,
        String password,
        String clientAddress
) {
    /**
     * Compact constructor with validation.
     */
    public LoginCommand {
        Objects.requireNonNull(email, "email cannot be null");
        Objects.requireNonNull(password, "password cannot be null");

        if (email.isBlank()) {
            throw new IllegalArgumentException("email cannot be blank");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("password cannot be blank");
        }
    }

    /**
     * Custom toString that prevents password leakage in logs.
     *
     * @return String representation without password
     */
    @Override
    public String toString() {
        return "LoginCommand[email=" + email + ", password=***, clientAddress=" + clientAddress + "]";
    }
}
