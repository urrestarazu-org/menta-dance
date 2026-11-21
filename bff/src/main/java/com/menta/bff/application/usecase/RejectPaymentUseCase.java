package com.menta.bff.application.usecase;

/**
 * Use case that rejects a payment awaiting manual verification (#370).
 *
 * <p>Part of Clean Architecture application layer. Whether the payment may be rejected is decided by the API
 * (ADR-0038): the BFF only sends the action. The call is not idempotent, so a second attempt reaches the caller
 * as {@code PaymentRefusalException} with {@code ILLEGAL_PAYMENT_STATE_TRANSITION}.
 * </p>
 */
public interface RejectPaymentUseCase {

    /**
     * Rejects the payment. The API mails the reason to the buyer.
     *
     * @param accessToken caller's access token
     * @param paymentId   identifier of the payment as it came in the URL
     * @param reason      why it is rejected, as submitted; it is trimmed and may not be blank nor longer than
     *                    500 characters
     * @throws com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException with
     *         {@code PAYMENT_NOT_FOUND} when the id is not a UUID and {@code INVALID_REQUEST} when the reason is
     *         unusable, both without calling the API, or any refusal of the API
     */
    void execute(String accessToken, String paymentId, String reason);
}
