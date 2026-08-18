package com.menta.billing.infrastructure.webhook;

/** Lifecycle of a {@code billing_webhook_inbox} row — mirrors {@code OutboxStatus}'s role for the outbox. */
public enum WebhookInboxStatus {
    RECEIVED,
    PROCESSED,
    RETRY_PENDING,
    RECONCILIATION_REQUIRED
}
