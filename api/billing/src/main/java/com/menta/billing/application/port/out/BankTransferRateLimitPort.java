package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.domain.model.PaymentId;
import java.util.UUID;

/**
 * Two independent budgets for the bank-transfer flow (design C7): different subjects, different
 * windows, different meanings. A shared structure would tie one budget's expiry to the other's.
 */
public interface BankTransferRateLimitPort {

    /** 10 bank-transfer subscription creations/user/day (design C2/C7), consumed before any write. */
    RateLimitDecision consumeSubscriptionCreation(UUID userId);

    /**
     * 3 proof uploads/payment/72h (design C7). Declared here so the port is complete from P2, but
     * only {@code SubmitPaymentProofUseCaseImpl} (P3c) calls it — consumed after content
     * validation passes, not on every request (design C7's one deliberate deviation from the
     * plans limiter).
     */
    RateLimitDecision consumeProofUpload(PaymentId paymentId);
}
