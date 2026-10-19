package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.PendingVerificationPage;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Out-port for persisting and querying {@link Payment} aggregates.
 *
 * <p>The JPA adapter requires {@code save} to run inside a caller-owned transaction, while its
 * reads join an existing transaction or open a read-only one. A payment is located by its id, by
 * the provider's payment id, or by the external reference generated at checkout. The two list
 * queries take primitive paging arguments so that no Spring type crosses into the application
 * layer.
 */
public interface PaymentRepository {

    /** Persists the payment, inserting or updating it by id, and returns the stored state. */
    Payment save(Payment payment);

    /** Finds a payment by its id. */
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

    /**
     * The admin inbox (#33, US-BILLING-005, design C10): payments {@code
     * AwaitingManualVerification}, oldest-first. Takes primitives, not a Spring {@code Pageable} —
     * same precedent as {@link #findExpirableBankTransferIds}, so no Spring type crosses into
     * {@code application}. The caller is responsible for rejecting {@code size > 50}; this port
     * does not clamp.
     */
    PendingVerificationPage findAwaitingManualVerification(int page, int size);
}
