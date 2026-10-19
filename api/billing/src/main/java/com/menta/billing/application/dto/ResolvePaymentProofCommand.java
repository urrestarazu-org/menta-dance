package com.menta.billing.application.dto;

import com.menta.billing.domain.model.ManualVerificationDecision;
import java.util.UUID;

/**
 * An admin's decision resolving a bank-transfer payment left in {@code
 * AwaitingManualVerification} (#31, US-BILLING-003, design D1). {@code reason} is present only
 * for {@link ManualVerificationDecision#REJECTED} — {@code null} on approve. {@code adminId}
 * identifies who decided, for the audit trail (#33, US-BILLING-005, design C7).
 */
public record ResolvePaymentProofCommand(
    String paymentId, ManualVerificationDecision decision, String reason, UUID adminId
) {
}
