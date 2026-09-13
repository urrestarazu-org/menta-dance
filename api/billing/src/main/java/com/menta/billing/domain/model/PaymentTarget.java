package com.menta.billing.domain.model;

/**
 * Identifies the business outcome a {@link Payment} is expected to fund.
 *
 * <p>This is neither a payment method nor a Mercado Pago transaction. It
 * answers only: <em>which business flow does this payment fund?</em>
 * A payment has exactly one target, so the type system prevents invalid states
 * such as a payment referring to both a physical session and a virtual plan.
 *
 * <p>The target intentionally stores only the owning module's stable reference,
 * not an entity from that module. This keeps Billing independent from Physical
 * and Virtual. Billing activates virtual subscription snapshots locally; the
 * application composition layer owns physical capacity orchestration
 * (ADR-0028 and ADR-0039).
 *
 * <p>The persistence adapter represents this sealed hierarchy as a modality and
 * a target reference. New target types therefore require an explicit domain and
 * persistence change instead of silently adding another nullable identifier.
 */
public sealed interface PaymentTarget {

    /**
     * Funds a physical-course purchase priced by a {@code PhysicalCourseQuote}.
     *
     * <p>The reference is the {@code quoteId}, not a session id (design A5):
     * a {@code MONTHLY} purchase has no single session to represent, only a
     * quote that snapshots {@code purchaseType}, {@code courseId},
     * {@code scheduledSessionCount} and {@code selectedSessionId} —
     * everything confirmation needs. At confirmation time, {@code api:app}
     * resolves this quote into the concrete eligible session set (Billing's
     * {@code CoveragePlanner}) and assigns capacity for each of them; Billing
     * never owns, loads, or writes the Physical module's session entities
     * directly.
     */
    record Physical(String quoteId) implements PaymentTarget {
        public Physical {
            if (quoteId == null || quoteId.isBlank()) {
                throw new IllegalArgumentException("quoteId cannot be null or blank");
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
