package com.menta.virtual.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA persistence model for the virtual_lessons table. {@code courseId} is
 * denormalized here (in addition to {@code moduleId}) so counting lessons,
 * summing duration and resolving ownership per course is a flat {@code
 * WHERE course_id IN (...)} — never a JOIN through modules.
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

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /** {@code null} means no video assigned yet — {@code VirtualLesson.isComplete()} depends on this being reachable. */
    @Column(name = "video_id")
    private String videoId;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "is_free", nullable = false)
    private boolean free;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected VirtualLessonJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public VirtualLessonJpaEntity(
        UUID id, UUID moduleId, UUID courseId, String title, String description, String videoId,
        int durationMinutes, boolean free, int displayOrder
    ) {
        this.id = id;
        this.moduleId = moduleId;
        this.courseId = courseId;
        this.title = title;
        this.description = description;
        this.videoId = videoId;
        this.durationMinutes = durationMinutes;
        this.free = free;
        this.displayOrder = displayOrder;
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

    public String getDescription() {
        return description;
    }

    public String getVideoId() {
        return videoId;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public boolean isFree() {
        return free;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
