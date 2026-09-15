package com.menta.physical.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for the physical_capacity_assignments table.
 * {@code JpaPhysicalCapacityAssignmentAdapter} owns the write path (design
 * §5.3) via the {@code PhysicalCapacityAssignmentWriter} port; {@link
 * PhysicalSessionJpaRepository} still reads this table directly via a
 * native COUNT subquery for availability.
 *
 * <p>{@code uniqueConstraints} mirrors V7's real
 * {@code uq_physical_assignment_session_student} DDL exactly (#41 PR8): the
 * integration-test profile runs with {@code ddl-auto=create-drop} and
 * Flyway disabled, so Hibernate derives the schema purely from this
 * annotation. Without it, the test schema silently drops the one DB-level
 * guarantee a redelivered outbox event relies on to insert zero additional
 * rows for an already-claimed (session, student) pair — a schema/production
 * parity gap this entity mapping must not reintroduce.</p>
 */
@Entity
@Table(
    name = "physical_capacity_assignments",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_physical_assignment_session_student", columnNames = {"session_id", "student_id"}
    )
)
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
