package com.menta.virtual.infrastructure.persistence.mapper;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;

/** Manual mapper JPA entity ↔ domain — no MapStruct (unused in this project, see #96). */
public final class VirtualLessonJpaMapper {

    private VirtualLessonJpaMapper() {
    }

    public static VirtualLesson toDomain(VirtualLessonJpaEntity entity) {
        return new VirtualLesson(
            LessonId.of(entity.getId()), ModuleId.of(entity.getModuleId()), CourseId.of(entity.getCourseId()),
            entity.getTitle(), entity.getDescription(), entity.getVideoId(), entity.getDurationMinutes(),
            entity.isFree(), entity.getDisplayOrder()
        );
    }

    public static VirtualLessonJpaEntity toEntity(VirtualLesson lesson) {
        return new VirtualLessonJpaEntity(
            lesson.getId().getValue(), lesson.getModuleId().getValue(), lesson.getCourseId().getValue(),
            lesson.getTitle(), lesson.getDescription(), lesson.getVideoId(), lesson.getDurationMinutes(),
            lesson.isFree(), lesson.getOrder()
        );
    }
}
