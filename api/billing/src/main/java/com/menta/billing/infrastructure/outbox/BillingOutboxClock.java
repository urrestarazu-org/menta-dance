package com.menta.billing.infrastructure.outbox;

import java.time.Instant;

/**
 * Abstracted clock for outbox column defaults — tests pin created_at without
 * depending on the wall clock; production wiring uses
 * {@link BillingSystemOutboxClock}.
 */
public interface BillingOutboxClock {

    Instant now();
}
