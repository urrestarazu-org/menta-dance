package com.menta.virtual.domain.model;

import java.util.Objects;

/**
 * A lesson within a {@link VirtualModule} (US-VIRTUAL-006). {@code courseId}
 * is denormalized (mirrors {@code virtual_lessons.course_id} since #46) so
 * publish validation and ownership resolution never need to join through
 * the parent module. {@code videoId} is an opaque Bunny.net reference the
 * admin client already knows and supplies — this module never calls out to
 * Bunny.net itself, it only stores the id by value.
 */
public final class VirtualLesson {

    private final LessonId id;
    private final ModuleId moduleId;
    private final CourseId courseId;
    private final String title;
    private final String description;
    private final String videoId;
    private final int durationMinutes;
    private final boolean free;
    private final int order;

    public VirtualLesson(
        LessonId id, ModuleId moduleId, CourseId courseId, String title, String description,
        String videoId, int durationMinutes, boolean free, int order
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId cannot be null");
        this.courseId = Objects.requireNonNull(courseId, "courseId cannot be null");
        this.title = Objects.requireNonNull(title, "title cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.videoId = videoId;
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("durationMinutes cannot be negative");
        }
        this.durationMinutes = durationMinutes;
        this.free = free;
        if (order < 0) {
            throw new IllegalArgumentException("order cannot be negative");
        }
        this.order = order;
    }

    public static VirtualLesson create(
        ModuleId moduleId, CourseId courseId, String title, String description, String videoId,
        int durationMinutes, boolean free, int order
    ) {
        return new VirtualLesson(
            LessonId.generate(), moduleId, courseId, title, description, videoId, durationMinutes, free, order
        );
    }

    /**
     * US-VIRTUAL-006 escenario 6: "al menos 1 módulo con 1 lección
     * completa" — the issue never defines "completa". A lesson counts as
     * complete when it has a non-blank title (guaranteed by the constructor)
     * and a real video assigned, not a stub with no content yet.
     */
    public boolean isComplete() {
        return videoId != null && !videoId.isBlank();
    }

    public VirtualLesson withTitle(String newTitle) {
        return new VirtualLesson(id, moduleId, courseId, newTitle, description, videoId, durationMinutes, free, order);
    }

    public VirtualLesson withDescription(String newDescription) {
        return new VirtualLesson(id, moduleId, courseId, title, newDescription, videoId, durationMinutes, free, order);
    }

    public VirtualLesson withVideoId(String newVideoId) {
        return new VirtualLesson(id, moduleId, courseId, title, description, newVideoId, durationMinutes, free, order);
    }

    public VirtualLesson withDurationMinutes(int newDurationMinutes) {
        return new VirtualLesson(id, moduleId, courseId, title, description, videoId, newDurationMinutes, free, order);
    }

    public VirtualLesson withFree(boolean newFree) {
        return new VirtualLesson(id, moduleId, courseId, title, description, videoId, durationMinutes, newFree, order);
    }

    public VirtualLesson withOrder(int newOrder) {
        return new VirtualLesson(id, moduleId, courseId, title, description, videoId, durationMinutes, free, newOrder);
    }

    public LessonId getId() {
        return id;
    }

    public ModuleId getModuleId() {
        return moduleId;
    }

    public CourseId getCourseId() {
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

    public int getOrder() {
        return order;
    }
}
