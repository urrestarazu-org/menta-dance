package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.VirtualLessonManagementResult;

public record VirtualLessonManagementResponse(
    String lessonId,
    String moduleId,
    String courseId,
    String title,
    String description,
    String videoId,
    int durationMinutes,
    boolean free,
    int order
) {

    public static VirtualLessonManagementResponse from(VirtualLessonManagementResult result) {
        return new VirtualLessonManagementResponse(
            result.lessonId(),
            result.moduleId(),
            result.courseId(),
            result.title(),
            result.description(),
            result.videoId(),
            result.durationMinutes(),
            result.free(),
            result.order()
        );
    }
}
