package com.menta.billing.application.port.out;

/**
 * Application port for the Transactional Outbox Pattern on the billing side.
 * Mirrors {@code com.menta.auth.application.port.out.OutboxAppender}.
 *
 * <p>Implementation lives in
 * {@code com.menta.billing.infrastructure.outbox.BillingOutboxAppender}, the
 * only path that inserts a {@code common_outbox_events} row carrying a
 * billing-owned payload. Auth's {@code OutboxJpaAppender} is the counterpart
 * on the auth side; both modules map their own
 * {@code *OutboxRowJpaEntity} to the SAME physical table (V2 lines 34-50),
 * which is safe because the table has no outbound FKs and producer identity
 * is tracked via the per-module JPA class, not by sharing one.</p>
 */
public interface BillingOutboxAppenderPort {

    /**
     * Append an outbox event. MUST participate in the caller's transaction so
     * the COMMIT is shared with the originating domain mutation.
     *
     * @param eventType   never null (e.g. {@code BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED}).
     * @param aggregateId never null (e.g. {@code paymentId.toString()}).
     * @param payload     never null (JSON-encoded body).
     */
    void append(String eventType, String aggregateId, String payload);
}
