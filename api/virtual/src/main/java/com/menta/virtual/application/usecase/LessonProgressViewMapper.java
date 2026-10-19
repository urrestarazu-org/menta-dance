package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.domain.model.LessonProgress;

/** Maps {@link LessonProgress} to its read model, shared by all three progress use cases. */
final class LessonProgressViewMapper {

    private LessonProgressViewMapper() {
    }

    static LessonProgressView from(LessonProgress progress) {
        return new LessonProgressView(
            progress.getLessonId().toString(), progress.getPositionSeconds(), progress.isCompleted(),
            progress.getCompletedAt()
        );
    }
}
