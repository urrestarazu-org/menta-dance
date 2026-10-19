package com.menta.virtual.application.dto;

/** Management view of a course returned by the course use cases; ids, level and status are rendered as strings. */
public record VirtualCourseManagementResult(
    String courseId,
    String title,
    String shortDescription,
    String description,
    String professorId,
    String imageUrl,
    String category,
    String level,
    boolean premium,
    String status
) {
}
