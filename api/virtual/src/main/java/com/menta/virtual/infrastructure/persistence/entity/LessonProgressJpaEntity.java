package com.menta.virtual.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for {@code virtual_lesson_progress} (V19). One row per (user, lesson). */
@Entity
@Table(name = "virtual_lesson_progress")
public class LessonProgressJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "lesson_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID lessonId;

    @Column(name = "course_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "position_seconds", nullable = false)
    private int positionSeconds;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Resume ordering key (Slice 3); {@code null} means "never saved a position". */
    @Column(name = "position_updated_at")
    private Instant positionUpdatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LessonProgressJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public LessonProgressJpaEntity(
        UUID id, UUID userId, UUID lessonId, UUID courseId, int positionSeconds, boolean completed,
        Instant completedAt, Instant createdAt, Instant positionUpdatedAt, Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.lessonId = lessonId;
        this.courseId = courseId;
        this.positionSeconds = positionSeconds;
        this.completed = completed;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
        this.positionUpdatedAt = positionUpdatedAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public int getPositionSeconds() {
        return positionSeconds;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPositionUpdatedAt() {
        return positionUpdatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
