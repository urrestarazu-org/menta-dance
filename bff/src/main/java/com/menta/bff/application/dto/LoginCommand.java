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
 */
public record LoginCommand(
        String email,
        String password
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
        return "LoginCommand[email=" + email + ", password=***]";
    }
}
