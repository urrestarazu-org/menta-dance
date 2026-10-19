package com.menta.billing.domain.model;

/**
 * An admin's decision resolving a {@link Payment} left in {@link
 * PaymentStatus.AwaitingManualVerification} (D1, C4).
 */
public enum ManualVerificationDecision {
    APPROVED,
    REJECTED
}
