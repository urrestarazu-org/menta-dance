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
 * JPA entity for the {@code common_outbox_events} table (ADR-0030).
 *
 * <h2>Purpose in the Transactional Outbox Pattern</h2>
 * <p>
 * This entity represents a single outbox event row. Each row is an <b>intent</b>
 * to notify consumers about something that happened in the system. The row is
 * written atomically with the domain mutation, ensuring the event exists if and
 * only if the business operation committed successfully.
 * </p>
 *
 * <h2>Key columns</h2>
 * <ul>
 *   <li><b>id</b>: Auto-increment PK for fast writes (no UUID generation overhead)</li>
 *   <li><b>event_id</b>: ULID (26 chars) for producer-side idempotency and ordering</li>
 *   <li><b>event_type</b>: Dotted path like "auth.AuthUserLoggedIn" for routing</li>
 *   <li><b>aggregate_id</b>: Business key (e.g., jti, userId) for correlation</li>
 *   <li><b>payload</b>: JSON body with event details</li>
 *   <li><b>status</b>: Lifecycle state (PENDING → PROCESSED or FAILED)</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <pre>{@code
 * PENDING ──(reconciler processes)──→ PROCESSED
 *    │
 *    └──(error)──→ retry with backoff ──(max retries)──→ FAILED
 * }</pre>
 *
 * <h2>Ownership</h2>
 * <p>
 * The <b>appender</b> ({@code OutboxJpaAppender}) writes new rows with status=PENDING.
 * The <b>reconciler</b> ({@code OutboxBlacklistReconciler}) reads, processes, and
 * updates the lifecycle columns (attempts, last_error, next_retry_at, processed_at).
 * </p>
 *
 * @see com.menta.auth.infrastructure.outbox.OutboxJpaAppender
 * @see com.menta.shared.outbox.OutboxStatus
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
