package com.menta.auth.infrastructure.outbox.persistence;

/**
 * Identifier generator that returns a fresh unique string per outbox row.
 *
 * Implementations MUST guarantee uniqueness across concurrent producers.
 * Production default is the 26-char ULID (lexicographically sortable, K-Sortable
 * 80 bits of time + 48 bits of randomness — see design.md Decision 6).
 *
 * Defined as an interface so tests can pin a deterministic value
 * without depending on the runtime clock or entropy source.
 */
public interface UlidGenerator {

    /**
     * @return a fresh unique identifier suitable for the
     *         common_outbox_events.event_id CHAR(26) column.
     */
    String next();
}
