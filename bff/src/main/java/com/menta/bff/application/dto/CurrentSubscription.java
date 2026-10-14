package com.menta.bff.application.dto;

import java.time.Instant;

/**
 * The student's current subscription as the Billing API reports it.
 *
 * <p>Sealed so a consumer must handle every state: {@link Active}, {@link Pending},
 * {@link Expired} or {@link None} (the student has never subscribed).
 * </p>
 */
public sealed interface CurrentSubscription {

    /**
     * A subscription in force.
     *
     * @param endDate instant on which the subscription ends
     */
    record Active(Instant endDate) implements CurrentSubscription {
    }

    /**
     * A subscription waiting for its payment.
     *
     * @param subscriptionId full subscription identifier, shown to the student as a reference code
     */
    record Pending(String subscriptionId) implements CurrentSubscription {
    }

    /**
     * A subscription that already ended.
     *
     * @param endDate instant on which the subscription ended
     */
    record Expired(Instant endDate) implements CurrentSubscription {
    }

    /** The student has no subscription at all. */
    record None() implements CurrentSubscription {
    }
}
