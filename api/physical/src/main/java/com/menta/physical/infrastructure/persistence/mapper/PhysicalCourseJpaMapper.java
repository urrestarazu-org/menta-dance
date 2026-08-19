package com.menta.physical.infrastructure.persistence.mapper;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import java.time.DayOfWeek;
import java.time.Instant;

/** Manual mapper JPA entity ↔ domain — no MapStruct (unused in this project, see #96). */
public final class PhysicalCourseJpaMapper {

    private PhysicalCourseJpaMapper() {
    }

    public static PhysicalCourse toDomain(PhysicalCourseJpaEntity entity) {
        return new PhysicalCourse(
            CourseId.of(entity.getId()),
            entity.getTitle(),
            entity.getDescription(),
            entity.getProfessorId(),
            entity.getProfessorName(),
            DayOfWeek.valueOf(entity.getDayOfWeek()),
            entity.getStartTime(),
            entity.getDurationMinutes(),
            PhysicalCourseLevel.valueOf(entity.getLevel()),
            entity.getCapacity(),
            entity.getStatus()
        );
    }

    /**
     * {@code createdAt} must come from the existing row for an update (the
     * column is {@code updatable = false}) — the caller looks it up before
     * calling this, since the domain model itself carries no audit
     * timestamps. {@code updatedAt} is always "now".
     */
    public static PhysicalCourseJpaEntity toEntity(PhysicalCourse course, Instant createdAt, Instant updatedAt) {
        return new PhysicalCourseJpaEntity(
            course.getId().getValue(),
            course.getTitle(),
            course.getDescription(),
            course.getProfessorId(),
            course.getProfessorName(),
            course.getDayOfWeek().name(),
            course.getStartTime(),
            course.getDurationMinutes(),
            course.getLevel().name(),
            course.getCapacity(),
            course.getStatus(),
            createdAt,
            updatedAt
        );
    }
}
