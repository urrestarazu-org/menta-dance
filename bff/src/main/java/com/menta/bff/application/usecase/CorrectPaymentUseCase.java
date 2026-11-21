package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CorrectionDecision;

/**
 * Use case that imposes an outcome on a payment that needs reconciliation (#370).
 *
 * <p>Part of Clean Architecture application layer. Whether the payment admits a correction (only one in
 * {@code RECONCILIATION_REQUIRED} does) is decided by the API (ADR-0038): the BFF only sends the action. The
 * call is not idempotent, so a second attempt reaches the caller as {@code PaymentRefusalException} with
 * {@code ILLEGAL_PAYMENT_STATE_TRANSITION}.
 * </p>
 */
public interface CorrectPaymentUseCase {

    /**
     * Corrects the payment.
     *
     * @param accessToken caller's access token
     * @param paymentId   identifier of the payment as it came in the URL
     * @param decision    the outcome to impose
     * @param reason      why the correction is made, as submitted; trimmed, not blank, at most 500 characters
     * @param evidence    verifiable evidence for it, as submitted; trimmed, not blank, at most 500 characters
     * @throws com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException with
     *         {@code PAYMENT_NOT_FOUND} when the id is not a UUID and {@code INVALID_REQUEST} when the decision
     *         is missing or the reason or evidence is unusable, both without calling the API, or any refusal of
     *         the API
     */
    void execute(String accessToken, String paymentId, CorrectionDecision decision, String reason,
            String evidence);
}
