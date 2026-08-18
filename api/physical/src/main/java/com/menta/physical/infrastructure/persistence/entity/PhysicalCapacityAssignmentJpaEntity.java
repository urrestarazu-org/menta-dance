package com.menta.physical.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for the physical_capacity_assignments table. No
 * production adapter writes through this entity yet (the write/reservation
 * path is a separate, future issue) — {@link PhysicalSessionJpaRepository}
 * reads this table directly via a native COUNT subquery instead. This
 * mapping exists so Hibernate manages its schema and tests can seed rows.
 */
@Entity
@Table(name = "physical_capacity_assignments")
public class PhysicalCapacityAssignmentJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PhysicalCapacityAssignmentJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PhysicalCapacityAssignmentJpaEntity(UUID id, UUID sessionId, UUID studentId, Instant createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
