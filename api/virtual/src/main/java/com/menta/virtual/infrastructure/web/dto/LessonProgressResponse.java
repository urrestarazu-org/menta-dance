package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.LessonProgressView;
import java.time.Instant;

/** Wire response shared by the save, read, and complete progress endpoints. */
public record LessonProgressResponse(String lessonId, int positionSeconds, boolean completed, Instant completedAt) {

    public static LessonProgressResponse from(LessonProgressView view) {
        return new LessonProgressResponse(
            view.lessonId(), view.positionSeconds(), view.completed(), view.completedAt()
        );
    }
}
