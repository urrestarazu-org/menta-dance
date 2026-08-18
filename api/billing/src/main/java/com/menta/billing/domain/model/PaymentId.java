package com.menta.billing.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object identifying a {@link Payment}. */
public final class PaymentId {

    private final UUID value;

    private PaymentId(UUID value) {
        this.value = value;
    }

    public static PaymentId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("PaymentId cannot be null");
        }
        return new PaymentId(value);
    }

    public static PaymentId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PaymentId cannot be null or empty");
        }
        try {
            return new PaymentId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid PaymentId format: " + value, e);
        }
    }

    public static PaymentId generate() {
        return new PaymentId(UUID.randomUUID());
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
        PaymentId paymentId = (PaymentId) o;
        return Objects.equals(value, paymentId.value);
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
