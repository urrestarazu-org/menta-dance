package com.menta.billing.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object identifying a {@link PaymentProof} (#31, US-BILLING-003, design C5). */
public final class PaymentProofId {

    private final UUID value;

    private PaymentProofId(UUID value) {
        this.value = value;
    }

    public static PaymentProofId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("PaymentProofId cannot be null");
        }
        return new PaymentProofId(value);
    }

    public static PaymentProofId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PaymentProofId cannot be null or empty");
        }
        try {
            return new PaymentProofId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid PaymentProofId format: " + value, e);
        }
    }

    public static PaymentProofId generate() {
        return new PaymentProofId(UUID.randomUUID());
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
        PaymentProofId that = (PaymentProofId) o;
        return Objects.equals(value, that.value);
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
