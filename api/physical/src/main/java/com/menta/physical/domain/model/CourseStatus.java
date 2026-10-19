package com.menta.physical.domain.model;

/** Lifecycle state of a {@link PhysicalCourse}. Only {@code ACTIVE} courses are ever listed. */
public enum CourseStatus {
    ACTIVE,
    INACTIVE
}
