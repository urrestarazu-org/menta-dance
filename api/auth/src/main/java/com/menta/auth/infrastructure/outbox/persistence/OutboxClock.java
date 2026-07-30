package com.menta.auth.infrastructure.outbox.persistence;

import java.time.Instant;

/**
 * Abstracted clock for outbox timestamp generation.
 *
 * Tests stub this to pin created_at assertions; production wiring uses
 * SystemOutboxClock which delegates to Instant.now().
 */
public interface OutboxClock {

    Instant now();
}
