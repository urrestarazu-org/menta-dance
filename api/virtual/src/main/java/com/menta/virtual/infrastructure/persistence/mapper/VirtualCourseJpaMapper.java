package com.menta.virtual.infrastructure.persistence.mapper;

import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import java.time.Instant;

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
            entity.getDescription(),
            entity.getProfessorId(),
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

    /** Management-view mapping, no aggregate counts needed by the caller (US-VIRTUAL-006). */
    public static VirtualCourse toDomain(VirtualCourseJpaEntity entity) {
        return toDomain(entity, 0, 0, 0);
    }

    /**
     * {@code createdAt} must come from the existing row for an update (the
     * column is {@code updatable = false}) — the caller looks it up before
     * calling this, since the domain model itself carries no audit
     * timestamps. {@code updatedAt} is always "now".
     */
    public static VirtualCourseJpaEntity toEntity(VirtualCourse course, Instant createdAt, Instant updatedAt) {
        return new VirtualCourseJpaEntity(
            course.getId().getValue(),
            course.getTitle(),
            course.getShortDescription(),
            course.getDescription(),
            course.getProfessorId(),
            course.getImageUrl(),
            course.getCategory().getValue(),
            course.getLevel().name(),
            course.isPremium(),
            course.getStatus(),
            createdAt,
            updatedAt
        );
    }
}
