package com.menta.auth.infrastructure.persistence.entity;

import java.util.Objects;

/**
 * Strongly-typed wrapper around the BIGINT AUTO_INCREMENT PK of
 * common_outbox_events. Used internally to disambiguate row identifiers
 * from generic Long; the persistence layer JPA-maps the underlying value.
 */
public record OutboxRowIdMarker(Long value) {

    public OutboxRowIdMarker {
        if (value == null) {
            throw new IllegalArgumentException("OutboxRowIdMarker value cannot be null");
        }
    }

    public static OutboxRowIdMarker of(long value) {
        return new OutboxRowIdMarker(value);
    }

    @Override
    public String toString() {
        return "OutboxRowIdMarker{" + value + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutboxRowIdMarker that)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
