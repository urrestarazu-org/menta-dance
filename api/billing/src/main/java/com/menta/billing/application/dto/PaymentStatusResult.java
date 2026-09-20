package com.menta.billing.application.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Output of {@link com.menta.billing.application.port.in.GetPaymentUseCase} (#31, US-BILLING-003,
 * design C9).
 *
 * @param status one of {@code AWAITING_PROVIDER}, {@code AWAITING_MANUAL_VERIFICATION}, {@code
 *     RECONCILIATION_REQUIRED}, {@code COMPLETED}, {@code REJECTED}, {@code CANCELLED} or {@code
 *     EXPIRED} — the same {@code status_type} vocabulary {@code PaymentJpaMapper} persists.
 * @param updatedAt derived, never a stored column (design C9): {@code
 *     Payment.statusChangedAt().orElse(createdAt)} — when this payment's status changed, or {@code
 *     createdAt} if it never has.
 */
public record PaymentStatusResult(String status, Instant createdAt, Instant updatedAt) {

    public PaymentStatusResult {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status cannot be null or blank");
        }
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }
}
