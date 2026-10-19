package com.menta.billing.application.dto;

import com.menta.billing.domain.model.ManualVerificationDecision;
import java.util.UUID;

/**
 * An admin's exceptional correction out of {@code ReconciliationRequired} (#33, US-BILLING-005,
 * design D9/C1/C7). {@code reason} and {@code evidence} are both mandatory — unlike {@link
 * ResolvePaymentProofCommand}, where {@code reason} is present only on rejection.
 */
public record CorrectPaymentCommand(
    String paymentId, ManualVerificationDecision decision, String reason, String evidence, UUID adminId
) {
}
