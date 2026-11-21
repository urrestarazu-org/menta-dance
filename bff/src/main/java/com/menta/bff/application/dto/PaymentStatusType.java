package com.menta.bff.application.dto;

/** Where a payment stands. {@code UNKNOWN} stands for a value this BFF does not know yet. */
public enum PaymentStatusType {
    AWAITING_PROVIDER,
    AWAITING_MANUAL_VERIFICATION,
    RECONCILIATION_REQUIRED,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    UNKNOWN
}
