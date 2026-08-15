package com.menta.app.outbox;

import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;

/**
 * Applies the side effect for one explicitly supported outbox event type.
 *
 * <p>The reconciliation worker resolves exactly one handler before completing
 * a row. Implementations must propagate side-effect failures so the worker can
 * retain the existing FAILED/backoff lifecycle.</p>
 */
public interface OutboxEventHandler {

    /**
     * Returns whether this handler is the declared consumer for the event type.
     *
     * @param eventType persisted public outbox event type
     * @return {@code true} only for event types handled by this instance
     */
    boolean supports(String eventType);

    /**
     * Applies the side effect for a row already matched by {@link #supports(String)}.
     *
     * @param row outbox row to process
     */
    void handle(OutboxRowJpaEntity row);
}
