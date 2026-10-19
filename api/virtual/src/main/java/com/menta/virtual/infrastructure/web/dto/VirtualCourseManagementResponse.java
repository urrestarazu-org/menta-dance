package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;

/** Wire response for a course in the management endpoints. */
public record VirtualCourseManagementResponse(
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

    /** Maps the application result to the wire response. */
    public static VirtualCourseManagementResponse from(VirtualCourseManagementResult result) {
        return new VirtualCourseManagementResponse(
            result.courseId(),
            result.title(),
            result.shortDescription(),
            result.description(),
            result.professorId(),
            result.imageUrl(),
            result.category(),
            result.level(),
            result.premium(),
            result.status()
        );
    }
}
