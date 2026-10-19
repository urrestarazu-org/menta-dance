package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.domain.model.ManualVerificationDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/v1/admin/billing/payments/{id}/corrections} (#33, US-BILLING-005,
 * design D9/C1). Unlike {@link RejectPaymentRequest}, both {@code reason} and {@code evidence} are
 * mandatory regardless of {@code decision} — bean validation rejects a blank/absent value with
 * {@code 400} before the use case runs, the same mechanism the reject endpoint already uses.
 */
public record CorrectPaymentRequest(
    @NotNull ManualVerificationDecision decision, @NotBlank String reason, @NotBlank String evidence
) {
}
