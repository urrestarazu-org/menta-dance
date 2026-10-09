package com.menta.bff.application.dto;

import java.util.Objects;

/**
 * Command DTO asking for a new activation email.
 *
 * @param email         Email address of the account that should receive the message
 * @param clientAddress Canonical originating address of the client (ADR-0035).
 *     Nullable: when it cannot be established the Auth API falls back to the
 *     peer address it observes.
 */
public record ResendActivationCommand(String email, String clientAddress) {

    /**
     * Compact constructor with validation.
     */
    public ResendActivationCommand {
        Objects.requireNonNull(email, "email cannot be null");
        if (email.isBlank()) {
            throw new IllegalArgumentException("email cannot be blank");
        }
    }
}
