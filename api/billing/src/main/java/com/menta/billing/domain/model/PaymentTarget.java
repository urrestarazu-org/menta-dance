package com.menta.billing.domain.model;

/**
 * Identifies the business outcome a {@link Payment} is expected to fund.
 *
 * <p>This is neither a payment method nor a Mercado Pago transaction. It
 * answers only: <em>what should Billing fulfil if this payment is confirmed?</em>
 * A payment has exactly one target, so the type system prevents invalid states
 * such as a payment referring to both a physical session and a virtual plan.
 *
 * <p>The target intentionally stores only the owning module's stable reference,
 * not an entity from that module. This keeps Billing independent from Physical
 * and Virtual while allowing payment confirmation to trigger the appropriate
 * outcome: a physical target creates a purchase for a session; a virtual target
 * activates a subscription for a plan.
 *
 * <p>The persistence adapter represents this sealed hierarchy as a modality and
 * a target reference. New target types therefore require an explicit domain and
 * persistence change instead of silently adding another nullable identifier.
 */
public sealed interface PaymentTarget {

    /**
     * Funds one place in a scheduled physical session.
     *
     * <p>After confirmation, Billing creates the corresponding purchase and
     * delegates capacity assignment to Physical through its port. The
     * {@code sessionId} is a reference only; Billing never owns or loads the
     * Physical module's session entity.
     */
    record Physical(String sessionId) implements PaymentTarget {
        public Physical {
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalArgumentException("sessionId cannot be null or blank");
            }
        }
    }

    /**
     * Funds a subscription to a virtual plan, not a single course.
     *
     * <p>After confirmation, Billing activates the subscription and freezes the
     * plan's current course list as its access snapshot. Later edits to the
     * plan cannot change that already activated subscription, so this target
     * carries the {@code planId} and nothing else.
     */
    record Virtual(String planId) implements PaymentTarget {
        public Virtual {
            if (planId == null || planId.isBlank()) {
                throw new IllegalArgumentException("planId cannot be null or blank");
            }
        }
    }
}
