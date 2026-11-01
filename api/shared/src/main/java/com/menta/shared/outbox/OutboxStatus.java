package com.menta.shared.outbox;

/**
 * Lifecycle status of an outbox event row.
 *
 * <p>- PENDING: persisted post-commit, waiting for the reconciler side-effect.
 * - COMPLETED: reconciler successfully applied the side-effect.
 * - FAILED: the last attempt failed; it is retried after a backoff until the
 *   attempt cap is reached.
 * - DEAD: terminal. The row reached the attempt cap and the reconciler no longer
 *   picks it up; it is kept for inspection and manual requeue (#672).
 */
public enum OutboxStatus {
    PENDING,
    COMPLETED,
    FAILED,
    DEAD
}
