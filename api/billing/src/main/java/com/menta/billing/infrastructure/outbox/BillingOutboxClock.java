package com.menta.billing.infrastructure.outbox;

import java.time.Instant;

/**
 * Abstracted clock for outbox column defaults — tests pin created_at without
 * depending on the wall clock; production wiring uses
 * {@link BillingSystemOutboxClock}.
 */
public interface BillingOutboxClock {

    /**
     * Returns the instant stamped on a new outbox row as its creation time.
     *
     * <p>Production wiring uses the system clock; tests pin it.
     */
    Instant now();
}
