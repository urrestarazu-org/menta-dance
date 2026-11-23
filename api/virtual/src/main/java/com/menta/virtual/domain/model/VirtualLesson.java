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

    /**
     * Creates a lesson with a fresh {@link LessonId}; the constructor rules apply, so duration and
     * order must not be negative. A {@code null} video id creates a stub lesson with no video yet;
     * a blank one is rejected (see {@link #withVideoId(String)}).
     *
     * @throws IllegalArgumentException if {@code videoId} is non-null and blank
     */
    public static VirtualLesson create(
        ModuleId moduleId, CourseId courseId, String title, String description, String videoId,
        int durationMinutes, boolean free, int order
    ) {
        requireNotBlankWhenPresent(videoId);
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

    /** Returns a copy of this lesson with another title; the lesson itself is immutable. */
    public VirtualLesson withTitle(String newTitle) {
        return new VirtualLesson(id, moduleId, courseId, newTitle, description, videoId, durationMinutes, free, order);
    }

    /** Returns a copy of this lesson with another description. */
    public VirtualLesson withDescription(String newDescription) {
        return new VirtualLesson(id, moduleId, courseId, title, newDescription, videoId, durationMinutes, free, order);
    }

    /**
     * Returns a copy of this lesson with another video id. A video cannot be cleared by blanking it
     * (#845): a blank id is rejected instead of leaving the lesson incomplete. This guard lives here
     * and in {@link #create}, not in the constructor, because the constructor also hydrates stored
     * rows, and an existing row may already hold an empty id.
     *
     * @throws IllegalArgumentException if {@code newVideoId} is non-null and blank
     */
    public VirtualLesson withVideoId(String newVideoId) {
        requireNotBlankWhenPresent(newVideoId);
        return new VirtualLesson(id, moduleId, courseId, title, description, newVideoId, durationMinutes, free, order);
    }

    /** Returns a copy of this lesson with another duration in minutes, which must not be negative. */
    public VirtualLesson withDurationMinutes(int newDurationMinutes) {
        return new VirtualLesson(id, moduleId, courseId, title, description, videoId, newDurationMinutes, free, order);
    }

    /** Returns a copy of this lesson with the free flag changed. */
    public VirtualLesson withFree(boolean newFree) {
        return new VirtualLesson(id, moduleId, courseId, title, description, videoId, durationMinutes, newFree, order);
    }

    /** Returns a copy of this lesson with another display order, which must not be negative. */
    public VirtualLesson withOrder(int newOrder) {
        return new VirtualLesson(id, moduleId, courseId, title, description, videoId, durationMinutes, free, newOrder);
    }

    private static void requireNotBlankWhenPresent(String videoId) {
        if (videoId != null && videoId.isBlank()) {
            throw new IllegalArgumentException("videoId cannot be blank");
        }
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
