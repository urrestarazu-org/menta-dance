package com.menta.bff.application.usecase;

/**
 * Use case that reads one payment for the administrator's verification screen (#370).
 *
 * <p>Part of Clean Architecture application layer. Failures of the API reach the caller as
 * {@code AdminBillingApiClient.PaymentRefusalException} or
 * {@code AdminBillingApiClient.ServiceUnavailableException}, so the screen words each one.
 * </p>
 */
public interface GetPaymentDetailUseCase {

    /**
     * Reads the payment.
     *
     * @param accessToken caller's access token
     * @param paymentId   identifier of the payment as it came in the URL
     * @return the payment, already worded for the screen
     * @throws com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException with
     *         {@code PAYMENT_NOT_FOUND} when the id is not a UUID, without calling the API
     */
    PaymentVerificationView execute(String accessToken, String paymentId);
}
