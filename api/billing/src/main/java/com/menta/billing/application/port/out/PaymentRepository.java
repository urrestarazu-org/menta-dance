package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(PaymentId id);

    /** Only ever finds a payment already bound to the provider's id — a fresh checkout has none yet. */
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    /**
     * The correlation key for a Checkout Pro payment (US-BILLING-010): we
     * generate this reference at checkout, the provider echoes it back in its
     * authenticated payment response, and that echo is the only trustworthy
     * way to reach a {@code Payment} that has no {@code providerPaymentId}
     * bound yet. Never resolved from an unsigned webhook payload.
     */
    Optional<Payment> findByExternalReference(String externalReference);

    /**
     * The 72h automatic expiry sweep's candidate set (#31, US-BILLING-003, design C6): a
     * bank-transfer payment still {@code AwaitingManualVerification}, created before {@code
     * createdBefore}, with no submitted proof — a submitted proof withholds automatic expiry.
     */
    List<UUID> findExpirableBankTransferIds(Instant createdBefore, int limit);
}
