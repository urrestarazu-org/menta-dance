package com.menta.virtual.application.dto;

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
