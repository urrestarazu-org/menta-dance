package com.menta.auth.infrastructure.outbox.persistence;

import java.time.Instant;

/**
 * Abstracted clock for outbox timestamp generation.
 *
 * <p>Tests stub this to pin created_at assertions; production wiring uses
 * SystemOutboxClock which delegates to Instant.now().
 */
public interface OutboxClock {

    /**
     * Returns the instant to stamp on a new outbox row.
     *
     * @return the current instant according to this clock
     */
    Instant now();
}
