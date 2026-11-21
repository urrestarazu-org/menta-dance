package com.menta.bff.application.usecase;

/**
 * Use case that approves a payment awaiting manual verification (#370).
 *
 * <p>Part of Clean Architecture application layer. Whether the payment may be approved is decided by the API
 * (ADR-0038): the BFF only sends the action. The call is not idempotent, so a second attempt reaches the caller
 * as {@code PaymentRefusalException} with {@code ILLEGAL_PAYMENT_STATE_TRANSITION}.
 * </p>
 */
public interface ApprovePaymentUseCase {

    /**
     * Approves the payment.
     *
     * @param accessToken caller's access token
     * @param paymentId   identifier of the payment as it came in the URL
     * @throws com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException with
     *         {@code PAYMENT_NOT_FOUND} when the id is not a UUID, without calling the API, or any refusal of
     *         the API
     */
    void execute(String accessToken, String paymentId);
}
