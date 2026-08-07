package com.menta.bff.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a user identifier.
 * <p>
 * Wraps UUID to provide type safety and domain semantics.
 * Part of domain layer with ZERO framework dependencies.
 * </p>
 *
 * @param value UUID representing the unique user identifier
 */
public record UserId(UUID value) {

    /**
     * Compact constructor with validation.
     */
    public UserId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    /**
     * Generates a new random UserId.
     *
     * @return New UserId with random UUID
     */
    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }
}
