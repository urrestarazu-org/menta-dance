package com.menta.virtual.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA persistence model for the virtual_lessons table. {@code courseId} is
 * denormalized here (in addition to {@code moduleId}) so counting lessons
 * and summing duration per course is a flat {@code WHERE course_id IN (...)}
 * — never a JOIN through modules. Content is out of scope (US-VIRTUAL-002).
 */
@Entity
@Table(name = "virtual_lessons")
public class VirtualLessonJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "module_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID moduleId;

    @Column(name = "course_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    protected VirtualLessonJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public VirtualLessonJpaEntity(UUID id, UUID moduleId, UUID courseId, String title, int durationMinutes) {
        this.id = id;
        this.moduleId = moduleId;
        this.courseId = courseId;
        this.title = title;
        this.durationMinutes = durationMinutes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getModuleId() {
        return moduleId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getTitle() {
        return title;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }
}
