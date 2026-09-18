package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import java.util.Optional;

public interface PurchaseRepository {

    Purchase save(Purchase purchase);

    /**
     * Attempts the insert in its OWN isolated transaction, independent of any
     * ambient transaction (#245). Returns {@code Optional.of(saved)} on
     * success; returns {@code Optional.empty()} — never propagates {@link
     * org.springframework.dao.DataIntegrityViolationException} — when it
     * loses a real concurrent race against {@code
     * uq_billing_purchases_payment_id}.
     */
    Optional<Purchase> saveIsolated(Purchase purchase);

    /**
     * A PLAIN, non-locking read — the pre-insert existence check. Never call
     * this immediately before a possible {@link #saveIsolated(Purchase)} for
     * the SAME {@code paymentId} from a DIFFERENT read (#245): see {@link
     * #findByPaymentIdForUpdate(PaymentId)} for why this method must stay
     * non-locking.
     */
    Optional<Purchase> findByPaymentId(PaymentId paymentId);

    /**
     * A LOCKING read (#245), required whenever the caller will decide a
     * WRITE in the SAME ambient transaction from what it reads here — under
     * REPEATABLE READ, {@link #findByPaymentId(PaymentId)} would silently
     * miss a row {@link #saveIsolated(Purchase)} committed moments earlier on
     * a different connection. Never call this immediately BEFORE a possible
     * {@link #saveIsolated(Purchase)} INSERT of the SAME {@code paymentId} in
     * the SAME transaction — the lock this takes on a non-existent row's
     * index gap would self-block that insert.
     */
    Optional<Purchase> findByPaymentIdForUpdate(PaymentId paymentId);
}
