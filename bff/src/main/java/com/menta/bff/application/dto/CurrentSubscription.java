package com.menta.bff.application.dto;

import java.time.Instant;

/**
 * The student's current subscription as the Billing API reports it.
 *
 * <p>Sealed so a consumer must handle every state: {@link Active}, {@link Pending},
 * {@link Expired} or {@link None} (the student has never subscribed).
 * </p>
 *
 * <p>The {@code planId} of every state is enrichment only: the plan name is resolved from the
 * public catalog, so a missing id degrades the label but never the state. The Billing API always
 * sends it; it is nullable here so a consumer that does not need it never fails on its absence.
 * </p>
 */
public sealed interface CurrentSubscription {

    /**
     * A subscription in force.
     *
     * @param planId        subscribed plan, or {@code null} when the API did not send it
     * @param endDate       instant on which the subscription ends
     * @param daysRemaining whole days left as the API computes them (rounded up, never negative),
     *                      or {@code null} when the API did not send them
     * @param expiringSoon  whether the API flags the subscription as about to expire
     */
    record Active(String planId, Instant endDate, Long daysRemaining, boolean expiringSoon)
            implements CurrentSubscription {
    }

    /**
     * A subscription waiting for its payment.
     *
     * @param subscriptionId full subscription identifier, shown to the student as a reference code
     * @param planId         plan being paid for, or {@code null} when the API did not send it
     */
    record Pending(String subscriptionId, String planId) implements CurrentSubscription {
    }

    /**
     * A subscription that already ended.
     *
     * @param planId  plan that ended, or {@code null} when the API did not send it
     * @param endDate instant on which the subscription ended
     */
    record Expired(String planId, Instant endDate) implements CurrentSubscription {
    }

    /** The student has no subscription at all. */
    record None() implements CurrentSubscription {
    }
}
