package com.menta.billing.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for the append-only billing_physical_course_pricing_revisions table. Never updated after insert. */
@Entity
@Table(name = "billing_physical_course_pricing_revisions")
public class PhysicalCoursePricingRevisionJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "course_id", columnDefinition = "VARCHAR(64)", nullable = false, updatable = false)
    private String courseId;

    @Column(name = "actor_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "reason", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "version", nullable = false, updatable = false)
    private int version;

    @Column(name = "previous_value", columnDefinition = "TEXT", updatable = false)
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "TEXT", updatable = false)
    private String newValue;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected PhysicalCoursePricingRevisionJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PhysicalCoursePricingRevisionJpaEntity(
        UUID id, String courseId, UUID actorId, String reason, int version, String previousValue, String newValue,
        Instant occurredAt
    ) {
        this.id = id;
        this.courseId = courseId;
        this.actorId = actorId;
        this.reason = reason;
        this.version = version;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCourseId() {
        return courseId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getReason() {
        return reason;
    }

    public int getVersion() {
        return version;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
