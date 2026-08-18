package com.menta.billing.domain.model;

import java.util.Objects;

/**
 * A course included in a {@link Plan}, referenced by value.
 *
 * <p>Deliberately carries only the cross-module {@code courseId} — never a
 * name, never any data owned by Virtual or Physical. Resolving a human name
 * is the application layer's job, via {@code CourseCatalogPort}: the domain
 * must not know how (or whether) that resolution happens.</p>
 */
public final class PlanCourse {

    private final String courseId;

    private PlanCourse(String courseId) {
        this.courseId = courseId;
    }

    public static PlanCourse of(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            throw new IllegalArgumentException("courseId cannot be null or empty");
        }
        return new PlanCourse(courseId);
    }

    public String getCourseId() {
        return courseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PlanCourse that = (PlanCourse) o;
        return courseId.equals(that.courseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courseId);
    }
}
