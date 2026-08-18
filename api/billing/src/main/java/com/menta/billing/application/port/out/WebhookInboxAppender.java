package com.menta.billing.application.port.out;

import java.time.Instant;

/**
 * Persists a verified webhook notification as {@code RECEIVED} in {@code
 * billing_webhook_inbox} (US-BILLING-002) — durable dedup, never Redis.
 */
public interface WebhookInboxAppender {

    /**
     * @param dedupeKey unique key identifying this exact notification
     *     (dataId + requestId) — a second call with the same key must not
     *     create a second row.
     * @return {@code true} if a new row was appended; {@code false} if
     *     {@code dedupeKey} already existed (duplicate/replay).
     */
    boolean appendIfNew(String dedupeKey, String providerPaymentId, String requestId, Instant receivedAt);
}
