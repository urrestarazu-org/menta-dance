package com.menta.billing.application.dto;

/**
 * A course included in a plan, enriched with its display name.
 *
 * @param courseName {@code null} when {@code CourseCatalogPort} could not
 *     resolve the course (unknown id, a course that is not publicly visible,
 *     an id that is not a valid course id, or a failing catalog lookup). The
 *     HTTP layer decides how to render that absence; the application layer
 *     never fabricates a name.
 */
public record PlanCourseResult(String courseId, String courseName) {
}
