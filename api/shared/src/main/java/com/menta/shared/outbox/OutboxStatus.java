package com.menta.shared.outbox;

/**
 * Lifecycle status of an outbox event row.
 *
 * - PENDING: persisted post-commit, waiting for the reconciler side-effect.
 * - COMPLETED: reconciler successfully applied the side-effect.
 * - FAILED: reconciler exhausted retries; transient backoff applies.
 */
public enum OutboxStatus {
    PENDING,
    COMPLETED,
    FAILED
}
