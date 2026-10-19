package com.menta.physical.application.dto;

/**
 * Management-view projection of a recurring physical course, already rendered as text and
 * primitives.
 *
 * <p>Returned by the course management use cases so the web layer never touches the domain {@code
 * PhysicalCourse}. Identifiers, {@code dayOfWeek}, {@code startTime}, {@code level} and {@code
 * status} are the string forms of their domain counterparts; it carries every status, including
 * {@code INACTIVE}.
 */
public record PhysicalCourseManagementResult(
    String courseId,
    String title,
    String description,
    String professorId,
    String professorName,
    String dayOfWeek,
    String startTime,
    int durationMinutes,
    String level,
    int capacity,
    String status
) {
}
