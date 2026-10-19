package com.menta.physical.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for the physical_capacity_holds table. {@link PhysicalSessionJpaRepository}
 * still reads this table directly via a native COUNT subquery for availability; this mapping
 * exists so Hibernate manages its schema and the write path (#208, US-PHYSICAL-004b) can persist
 * and update rows through it.
 */
@Entity
@Table(name = "physical_capacity_holds")
public class PhysicalCapacityHoldJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PhysicalCapacityHoldJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PhysicalCapacityHoldJpaEntity(
        UUID id, UUID sessionId, UUID paymentId, Instant expiresAt, Instant convertedAt, Instant createdAt
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.paymentId = paymentId;
        this.expiresAt = expiresAt;
        this.convertedAt = convertedAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConvertedAt() {
        return convertedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
