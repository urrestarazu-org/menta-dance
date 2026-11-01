package com.menta.app.outbox;

/**
 * Outcome of processing one outbox row, as seen by the reconciler's heartbeat decision.
 */
public enum OutboxProcessResult {

    /** The side effect was applied and the row is {@code COMPLETED}. */
    COMPLETED,

    /**
     * The row could not be dispatched (no handler, ambiguous handler, unreadable payload). Unrelated
     * to Redis health, so it does not block the heartbeat.
     */
    DISPATCH_FAILED,

    /**
     * The handler ran and its side effect failed. It may be a real Redis outage or a broken
     * projection, so the heartbeat stays blocked (fail-closed, ADR-0026).
     */
    SIDE_EFFECT_FAILED;

    /** Tells whether this outcome must keep the tick from writing the heartbeat. */
    public boolean blocksHeartbeat() {
        return this == SIDE_EFFECT_FAILED;
    }
}
