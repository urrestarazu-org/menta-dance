package com.menta.physical.infrastructure.persistence.repository;

/**
 * {@link PhysicalSessionAvailabilityProjection} plus the columns of the session's course that staff
 * listings need, computed by the join in {@link
 * PhysicalSessionJpaRepository#findManagedAcrossCoursesWithAvailability}.
 */
public interface PhysicalSessionWithCourseProjection extends PhysicalSessionAvailabilityProjection {

    /** Title of the session's course. */
    String getCourseTitle();

    /** Current duration of the session's course, in minutes. */
    Integer getCourseDurationMinutes();
}
