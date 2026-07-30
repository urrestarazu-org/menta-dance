package com.menta.auth.infrastructure.persistence.entity;

import com.menta.shared.outbox.OutboxStatus;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA binding for common_outbox_events (ADR-0026 durable-local outbox).
 *
 * Columns mirror the V2 DDL exactly. The PK is BIGINT AUTO_INCREMENT for
 * write performance; event_id (ULID, 26 chars) is the secondary UNIQUE that
 * guarantees producer-side idempotency. status is stored as VARCHAR so the
 * reconciler update path doesn't depend on enum ordinal sequencing.
 *
 * Lifecycle columns (attempts, last_error, next_retry_at, processed_at) are
 * managed by the reconciler (api:app / OutboxBlacklistReconciler). The
 * appender only touches status, event_id, event_type, aggregate_id, payload,
 * created_at.
 */
@Entity
@Table(name = "common_outbox_events")
public class OutboxRowJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "event_id", length = 26, nullable = false, updatable = false, unique = true, columnDefinition = "CHAR(26)")
    private String eventId;

    @Column(name = "event_type", length = 100, nullable = false, updatable = false)
    private String eventType;

    @Column(name = "aggregate_id", length = 64, nullable = false, updatable = false)
    private String aggregateId;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected OutboxRowJpaEntity() {
        // JPA — required no-arg ctor.
    }

    public OutboxRowJpaEntity(
        String eventId,
        String eventType,
        String aggregateId,
        String payload,
        OutboxStatus status,
        int attempts,
        String lastError,
        Instant nextRetryAt,
        Instant createdAt,
        Instant processedAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = status;
        this.attempts = attempts;
        this.lastError = lastError;
        this.nextRetryAt = nextRetryAt;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Test-only helper: force-assign the PK without touching @GeneratedValue.
     */
    public void forceId(long id) {
        setId(id);
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setStatus(OutboxStatus status) {
        this.status = status;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
