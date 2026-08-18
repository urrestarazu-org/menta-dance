package com.menta.physical.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for the physical_sessions table. */
@Entity
@Table(name = "physical_sessions")
public class PhysicalSessionJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    protected PhysicalSessionJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PhysicalSessionJpaEntity(UUID id, UUID courseId, Instant scheduledAt, int capacity) {
        this.id = id;
        this.courseId = courseId;
        this.scheduledAt = scheduledAt;
        this.capacity = capacity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public int getCapacity() {
        return capacity;
    }
}
