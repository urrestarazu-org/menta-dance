package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import java.util.Optional;

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
}
