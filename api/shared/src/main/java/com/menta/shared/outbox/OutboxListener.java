package com.menta.shared.outbox;

/**
 * Marker contract for cross-module outbox consumers.
 *
 * Implementations live in the consumer module's infrastructure layer and are
 * wired into the reconciler once consumers are added in a follow-up change.
 *
 * Functional interface so it can be expressed as a lambda when convenient.
 * Living in :api:shared with no framework imports (ADR-0021).
 */
@FunctionalInterface
public interface OutboxListener<E extends OutboxEvent> {

    /**
     * Invoked by the reconciler when an event matching the listener's filter
     * transitions from PENDING to side-effect-applied. Side-effects themselves
     * MUST be idempotent, re-derivable from the event payload, and tolerant of
     * replay.
     */
    void onEvent(E event);
}
