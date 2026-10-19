package com.menta.virtual.domain.model;

/** Lifecycle state of a {@link VirtualCourse}. Only {@code PUBLISHED} courses are ever shown publicly. */
public enum CourseStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
