package com.menta.physical.application.dto;

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
