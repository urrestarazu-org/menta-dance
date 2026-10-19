package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.domain.model.VirtualLesson;

final class VirtualLessonResultMapper {

    private VirtualLessonResultMapper() {
    }

    static VirtualLessonManagementResult toResult(VirtualLesson lesson) {
        return new VirtualLessonManagementResult(
            lesson.getId().toString(),
            lesson.getModuleId().toString(),
            lesson.getCourseId().toString(),
            lesson.getTitle(),
            lesson.getDescription(),
            lesson.getVideoId(),
            lesson.getDurationMinutes(),
            lesson.isFree(),
            lesson.getOrder()
        );
    }
}
