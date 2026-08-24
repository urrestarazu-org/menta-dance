package com.menta.billing.infrastructure.outbox;

import com.menta.shared.outbox.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA entity for the {@code common_outbox_events} table belonging to
 * {@code api:billing}.
 *
 * <p>This is a deliberate SECOND mapping of the SAME physical table: api:auth
 * already maps the table via its own {@code OutboxRowJpaEntity} (V2 lines
 * 34-50). Two JPA classes targeting one physical table is safe because
 * (a) the table has no outbound FKs; (b) Hibernate's auto-generated PK is
 * opaque to either side; (c) the {@code event_id} column has its own
 * UNIQUE constraint preventing dedupe clashes; (d) both modules ever
 * write/read only as part of the unified Spring transaction manager
 * registered in api:app's runtime classpath.</p>
 *
 * <p>The shape mirrors {@code com.menta.auth.infrastructure.persistence.
 * entity.OutboxRowJpaEntity} exactly so cross-module structural rules
 * remain symmetric.</p>
 *
 * @see com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity
 */
@Entity
@Table(name = "common_outbox_events")
public class BillingOutboxRowJpaEntity {

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

    protected BillingOutboxRowJpaEntity() {
        // JPA — required no-arg ctor.
    }

    public BillingOutboxRowJpaEntity(
        String eventId, String eventType, String aggregateId, String payload,
        OutboxStatus status, int attempts, String lastError, Instant nextRetryAt,
        Instant createdAt, Instant processedAt
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
}
