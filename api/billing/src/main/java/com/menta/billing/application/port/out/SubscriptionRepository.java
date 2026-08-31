package com.menta.billing.application.port.out;

import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Subscription;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for the lifecycle of a user's virtual-plan subscription.
 *
 * <p>A subscription is created before Checkout Pro is opened, activated after
 * a verified payment, and may later be cancelled when payment does not settle.
 * Implementations must preserve the subscription's course snapshot together
 * with its lifecycle state: the snapshot represents the courses bought at
 * activation time, not the plan's current contents.
 *
 * <p>This port deliberately separates an ordinary {@link #save(Subscription)}
 * from {@link #saveNewCheckout(Subscription)}. Only the latter claims a user's
 * single checkout slot atomically, preventing concurrent requests from opening
 * two charges for the same user.
 */
public interface SubscriptionRepository {

    /**
     * Persists a subscription that already exists.
     *
     * <p>Used for checkout details, activation, fulfillment outcomes, and
     * cancellation. If the subscription contains a course snapshot, it must be
     * stored consistently with the subscription state.
     */
    Subscription save(Subscription subscription);

    /**
     * Inserts a brand-new checkout, <em>claiming</em> the user's single
     * subscription slot through a unique constraint — never an
     * exists-then-insert race, same discipline as {@code
     * billing_webhook_inbox.dedupe_key} (US-BILLING-002).
     *
     * @throws SubscriptionAlreadyActiveException when the constraint rejects
     *     the insert because another checkout already holds the slot. The
     *     caller's transaction rolls back with it, so the losing request
     *     leaves no {@code Payment} and starts no provider charge.
     */
    Subscription saveNewCheckout(Subscription subscription);

    /**
     * Finds the subscription funded by a payment.
     *
     * <p>Payment verification uses this to activate or recover fulfillment for
     * the subscription created by its checkout. A returned subscription must
     * include its persisted course snapshot.
     */
    Optional<Subscription> findByPaymentId(PaymentId paymentId);

    /**
     * Finds the checkout created by the same user request.
     *
     * <p>The idempotency key belongs to a user, so both values are required.
     * This lets a replay return the original checkout instead of creating a
     * second payment preference or subscription.
     */
    Optional<Subscription> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /**
     * Finds the subscription currently preventing the user from opening a new checkout.
     *
     * <p>At most one result may exist: it is either {@code PENDING} while the
     * payment is unresolved, or {@code ACTIVE} after settlement. A cancelled
     * or expired subscription does not occupy the slot and must not be returned.
     */
    Optional<Subscription> findCurrentByUserId(UUID userId);

    /**
     * Returns every subscription for a student with its frozen course snapshot.
     *
     * <p>Entitlement reads use this rather than the checkout-slot lookup because
     * cancelled subscriptions no longer occupy a slot but can retain paid access
     * until their {@code endDate} (US-BILLING-011).</p>
     */
    java.util.List<Subscription> findAllByUserId(UUID userId);

    /**
     * Finds the subscription that is currently {@code ACTIVE} for a user, or empty if none is
     * (US-BILLING-011, self-service cancellation).
     *
     * <p>Unlike {@link #findCurrentByUserId(UUID)}, which also matches {@code PENDING}, this
     * filters strictly to {@code ACTIVE} so cancelling a still-unsettled checkout correctly
     * reports "no cancellable subscription" (404) instead of cancelling it.</p>
     */
    Optional<Subscription> findActiveByUserId(UUID userId);

    /**
     * Finds a subscription by its id regardless of status. The admin cancellation route
     * resolves any subscription this way before checking it is {@code ACTIVE} (US-BILLING-011).
     */
    Optional<Subscription> findById(UUID subscriptionId);
}
