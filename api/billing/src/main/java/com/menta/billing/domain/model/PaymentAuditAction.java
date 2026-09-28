package com.menta.billing.domain.model;

/**
 * The three decisions recorded in {@code billing_audit_log} (#33, US-BILLING-005, design D4/C7):
 * an admin approving or rejecting a payment left {@code AwaitingManualVerification}, or an
 * exceptional correction out of {@code ReconciliationRequired}.
 */
public enum PaymentAuditAction {
    APPROVE,
    REJECT,
    CORRECTION
}
