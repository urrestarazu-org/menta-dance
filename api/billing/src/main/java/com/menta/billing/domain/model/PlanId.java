package com.menta.billing.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object identifying a subscription {@link Plan}. */
public final class PlanId {

    private final UUID value;

    private PlanId(UUID value) {
        this.value = value;
    }

    /** Wraps an existing UUID; {@code null} is rejected with {@link IllegalArgumentException}. */
    public static PlanId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("PlanId cannot be null");
        }
        return new PlanId(value);
    }

    /**
     * Parses the canonical UUID text of a plan id.
     *
     * <p>{@code null}, blank and malformed input are all rejected with {@link
     * IllegalArgumentException}.
     */
    public static PlanId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PlanId cannot be null or empty");
        }
        try {
            return new PlanId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid PlanId format: " + value, e);
        }
    }

    /** Creates an id backed by a fresh random UUID. */
    public static PlanId generate() {
        return new PlanId(UUID.randomUUID());
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
        PlanId planId = (PlanId) o;
        return Objects.equals(value, planId.value);
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
