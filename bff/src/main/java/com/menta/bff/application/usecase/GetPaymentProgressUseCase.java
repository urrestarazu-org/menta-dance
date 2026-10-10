package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PaymentProgress;

/**
 * Use case that asks the Billing API where a payment stands. The answer is
 * informative only: the BFF never decides a payment.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface GetPaymentProgressUseCase {

    /**
     * Reads the payment status.
     *
     * @param accessToken caller's access token
     * @param paymentId   payment to read
     * @return where the payment stands
     * @throws com.menta.bff.application.port.out.BillingApiClient.NotFoundException
     *         if the payment does not exist or is not the caller's
     */
    PaymentProgress execute(String accessToken, String paymentId);
}
