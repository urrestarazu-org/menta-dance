package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.ExceptionPurchasePage;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import java.util.Optional;

/**
 * Out-port for persisting and querying {@link Purchase} aggregates, the physical fulfillment record
 * of a payment.
 *
 * <p>The methods follow the concurrency rules of the fulfillment transaction: the isolated insert,
 * the plain read and the locking read are deliberately separate, as described on each of them. The
 * JPA adapter requires an ambient transaction for {@code save} and for both reads by payment id,
 * while {@code saveIsolated} opens its own and {@code findInException} runs read-only on its own.
 */
public interface PurchaseRepository {

    /**
     * Persists the purchase, creating its ordered session rows only on the first save.
     *
     * <p>Later saves, such as a status transition, update the purchase row and leave the session
     * rows untouched.
     */
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

    /**
     * The admin EXCEPTION-purchases inbox (#237, design C1): purchases stuck in {@link
     * com.menta.billing.domain.model.FulfillmentStatus#EXCEPTION}, oldest-first by the joined
     * {@code Payment.createdAt}. Takes primitives, not a Spring {@code Pageable} — same precedent
     * as {@link PaymentRepository#findAwaitingManualVerification(int, int)}, so no Spring type
     * crosses into {@code application}. The caller is responsible for rejecting {@code size > 50};
     * this port does not clamp.
     */
    ExceptionPurchasePage findInException(int page, int size);
}
