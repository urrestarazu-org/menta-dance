package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PaymentProofFile;

/**
 * Use case of the bank transfer payment of a quote: start it and upload its proof. The Billing
 * API owns every decision, including when the payment is accredited.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface PayPhysicalPurchaseByTransferUseCase {

    /**
     * Starts the transfer payment. Repeating the same idempotency key returns the original payment.
     *
     * @param accessToken    caller's access token
     * @param quoteId        quote to pay
     * @param idempotencyKey key identifying this attempt, 1 to 128 characters
     * @return where to transfer, the amount and the reference
     */
    BankTransferInstructionsView start(String accessToken, String quoteId, String idempotencyKey);

    /**
     * Uploads the proof of the transfer; it only leaves the payment waiting for verification.
     *
     * @param accessToken caller's access token
     * @param paymentId   transfer payment the proof belongs to
     * @param proof       the file chosen by the student
     */
    void submitProof(String accessToken, String paymentId, PaymentProofFile proof);
}
