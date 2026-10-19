package com.menta.billing.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Input to {@link com.menta.billing.application.port.in.SubmitPaymentProofUseCase} (#31,
 * US-BILLING-003, design C8/C12).
 *
 * <p>{@code actingUserId} comes from the access token, never from the request body — the same
 * {@code actingUserId} pattern {@code SubscriptionController} uses. It is never trusted as
 * authorization on its own: the use case still compares it against the payment's own owner.</p>
 */
public record SubmitPaymentProofCommand(String paymentId, UUID actingUserId, PaymentProofUpload upload) {

    public SubmitPaymentProofCommand {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("paymentId cannot be null or blank");
        }
        Objects.requireNonNull(actingUserId, "actingUserId cannot be null");
        Objects.requireNonNull(upload, "upload cannot be null");
    }
}
