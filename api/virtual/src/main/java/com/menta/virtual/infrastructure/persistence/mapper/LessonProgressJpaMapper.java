package com.menta.virtual.infrastructure.persistence.mapper;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.LessonProgress;
import com.menta.virtual.domain.model.LessonProgressId;
import com.menta.virtual.infrastructure.persistence.entity.LessonProgressJpaEntity;
import java.time.Instant;

/** Manual mapper JPA entity ↔ domain — no MapStruct (unused in this project, see #96). */
public final class LessonProgressJpaMapper {

    private LessonProgressJpaMapper() {
    }

    public static LessonProgress toDomain(LessonProgressJpaEntity entity) {
        return new LessonProgress(
            LessonProgressId.of(entity.getId()), entity.getUserId(), LessonId.of(entity.getLessonId()),
            CourseId.of(entity.getCourseId()), entity.getPositionSeconds(), entity.isCompleted(),
            entity.getCompletedAt(), entity.getPositionUpdatedAt()
        );
    }

    /**
     * {@code createdAt} must come from the existing row for an update (the column is
     * {@code updatable = false}) — the caller looks it up before calling this, since the domain
     * model itself carries no audit timestamps. {@code updatedAt} is always "now".
     */
    public static LessonProgressJpaEntity toEntity(LessonProgress progress, Instant createdAt, Instant updatedAt) {
        return new LessonProgressJpaEntity(
            progress.getId().getValue(), progress.getUserId(), progress.getLessonId().getValue(),
            progress.getCourseId().getValue(), progress.getPositionSeconds(), progress.isCompleted(),
            progress.getCompletedAt(), createdAt, progress.getPositionUpdatedAt(), updatedAt
        );
    }
}
