package com.menta.auth.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a user identifier.
 */
public final class UserId {

    private final UUID value;

    private UserId(UUID value) {
        this.value = value;
    }

    /**
     * Wraps an existing UUID.
     *
     * @param value the identifier; must not be null
     * @return the identifier value object
     * @throws IllegalArgumentException when {@code value} is null
     */
    public static UserId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
        return new UserId(value);
    }

    /**
     * Parses an identifier from its canonical UUID string form.
     *
     * @param value the textual UUID; must not be null or blank
     * @return the identifier value object
     * @throws IllegalArgumentException when {@code value} is null, blank or not a valid UUID
     */
    public static UserId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }
        try {
            return new UserId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UserId format: " + value, e);
        }
    }

    /**
     * Generates a new random identifier.
     *
     * @return an identifier backed by a random (version 4) UUID
     */
    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserId userId = (UserId) o;
        return Objects.equals(value, userId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
