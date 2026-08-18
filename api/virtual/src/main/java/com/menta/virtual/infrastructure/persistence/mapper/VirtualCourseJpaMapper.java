package com.menta.virtual.infrastructure.persistence.mapper;

import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;

/** Manual mapper JPA entity ↔ domain — no MapStruct (unused in this project, see #96). */
public final class VirtualCourseJpaMapper {

    private VirtualCourseJpaMapper() {
    }

    public static VirtualCourse toDomain(
        VirtualCourseJpaEntity entity, int moduleCount, int lessonCount, int totalDurationMinutes
    ) {
        return new VirtualCourse(
            CourseId.of(entity.getId()),
            entity.getTitle(),
            entity.getShortDescription(),
            entity.getImageUrl(),
            CourseCategory.of(entity.getCategory()),
            CourseLevel.valueOf(entity.getLevel()),
            entity.isPremium(),
            entity.getStatus(),
            moduleCount,
            lessonCount,
            totalDurationMinutes
        );
    }
}
