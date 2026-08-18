package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.PhysicalCourseManagementResult;

public record PhysicalCourseManagementResponse(
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

    public static PhysicalCourseManagementResponse from(PhysicalCourseManagementResult result) {
        return new PhysicalCourseManagementResponse(
            result.courseId(),
            result.title(),
            result.description(),
            result.professorId(),
            result.professorName(),
            result.dayOfWeek(),
            result.startTime(),
            result.durationMinutes(),
            result.level(),
            result.capacity(),
            result.status()
        );
    }
}
