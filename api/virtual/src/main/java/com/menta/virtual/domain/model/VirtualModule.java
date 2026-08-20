package com.menta.virtual.domain.model;

import java.util.Objects;

/**
 * A module within a {@link VirtualCourse} (US-VIRTUAL-006). Only a
 * management-side aggregate — the public catalog (#46/#95) only ever needs
 * a {@code moduleCount}, never a module's own identity or content.
 */
public final class VirtualModule {

    private final ModuleId id;
    private final CourseId courseId;
    private final String title;
    private final int order;

    public VirtualModule(ModuleId id, CourseId courseId, String title, int order) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.courseId = Objects.requireNonNull(courseId, "courseId cannot be null");
        this.title = Objects.requireNonNull(title, "title cannot be null");
        if (order < 0) {
            throw new IllegalArgumentException("order cannot be negative");
        }
        this.order = order;
    }

    public static VirtualModule create(CourseId courseId, String title, int order) {
        return new VirtualModule(ModuleId.generate(), courseId, title, order);
    }

    public VirtualModule withTitle(String newTitle) {
        return new VirtualModule(id, courseId, newTitle, order);
    }

    public VirtualModule withOrder(int newOrder) {
        return new VirtualModule(id, courseId, title, newOrder);
    }

    public ModuleId getId() {
        return id;
    }

    public CourseId getCourseId() {
        return courseId;
    }

    public String getTitle() {
        return title;
    }

    public int getOrder() {
        return order;
    }
}
