package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.SubmitPaymentProofCommand;

/**
 * In-port for submitting (or replacing) a bank-transfer payment proof (#31, US-BILLING-003,
 * design C5/C12).
 */
public interface SubmitPaymentProofUseCase {

    /**
     * Stores a bank-transfer proof for a payment, replacing any previous one.
     *
     * <p>A missing payment, a payment owned by someone else and a payment that is no longer {@code
     * AwaitingManualVerification} all fail with the same {@code PaymentNotFoundException}, so
     * callers cannot probe other users' payments. The content is validated before the upload budget
     * is consumed, and a spent budget fails with {@code BankTransferRateLimitedException}. The new
     * blob is stored before its database row, a proof-submitted notification is sent, and the
     * previous blob is deleted last.
     */
    void submit(SubmitPaymentProofCommand command);
}
