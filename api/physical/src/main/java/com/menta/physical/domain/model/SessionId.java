package com.menta.physical.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object identifying a {@link PhysicalSession}. */
public final class SessionId {

    private final UUID value;

    private SessionId(UUID value) {
        this.value = value;
    }

    /** Wraps an existing UUID; {@code null} is rejected with {@link IllegalArgumentException}. */
    public static SessionId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("SessionId cannot be null");
        }
        return new SessionId(value);
    }

    /**
     * Parses the canonical UUID text of a session id.
     *
     * <p>{@code null}, blank and malformed input are all rejected with {@link
     * IllegalArgumentException}.
     */
    public static SessionId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SessionId cannot be null or empty");
        }
        try {
            return new SessionId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid SessionId format: " + value, e);
        }
    }

    /** Creates an id backed by a fresh random UUID. */
    public static SessionId generate() {
        return new SessionId(UUID.randomUUID());
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
        SessionId sessionId = (SessionId) o;
        return Objects.equals(value, sessionId.value);
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
