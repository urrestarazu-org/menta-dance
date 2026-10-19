package com.menta.billing.infrastructure.persistence.entity;

import com.menta.billing.infrastructure.webhook.WebhookInboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for {@code billing_webhook_inbox} — durable dedup + retry
 * bookkeeping for verified webhooks (US-BILLING-002). {@code dedupeKey}
 * carries the unique constraint that makes a duplicate/replay a no-op
 * insert, not a race-prone exists-then-insert.
 */
@Entity
@Table(name = "billing_webhook_inbox")
public class WebhookInboxJpaEntity {

    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "dedupe_key", nullable = false, unique = true, updatable = false)
    private String dedupeKey;

    @Column(name = "provider_payment_id", nullable = false, updatable = false)
    private String providerPaymentId;

    @Column(name = "request_id", nullable = false, updatable = false)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(30)")
    private WebhookInboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected WebhookInboxJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public WebhookInboxJpaEntity(
        String dedupeKey, String providerPaymentId, String requestId, WebhookInboxStatus status,
        int attemptCount, Instant nextAttemptAt, String lastError, Instant receivedAt, Instant processedAt
    ) {
        this.dedupeKey = dedupeKey;
        this.providerPaymentId = providerPaymentId;
        this.requestId = requestId;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
        this.receivedAt = receivedAt;
        this.processedAt = processedAt;
    }

    public Long getId() {
        return id;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public String getRequestId() {
        return requestId;
    }

    public WebhookInboxStatus getStatus() {
        return status;
    }

    public void setStatus(WebhookInboxStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
