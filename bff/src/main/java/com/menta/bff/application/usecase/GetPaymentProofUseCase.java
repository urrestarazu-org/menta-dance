package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PaymentProof;

/**
 * Use case that fetches the transfer proof of a payment for the administrator (#370).
 *
 * <p>Part of Clean Architecture application layer. Failures of the API reach the caller as
 * {@code AdminBillingApiClient.PaymentRefusalException} or
 * {@code AdminBillingApiClient.ServiceUnavailableException}.
 * </p>
 */
public interface GetPaymentProofUseCase {

    /**
     * Fetches the proof.
     *
     * @param accessToken caller's access token
     * @param paymentId   identifier of the payment as it came in the URL
     * @return the proof's bytes and type
     * @throws com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException with
     *         {@code PAYMENT_NOT_FOUND} when the id is not a UUID, without calling the API
     */
    PaymentProof execute(String accessToken, String paymentId);
}
