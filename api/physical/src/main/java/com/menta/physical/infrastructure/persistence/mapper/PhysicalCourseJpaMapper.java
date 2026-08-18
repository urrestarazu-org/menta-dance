package com.menta.physical.infrastructure.persistence.mapper;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import java.time.DayOfWeek;

/** Manual mapper JPA entity ↔ domain — no MapStruct (unused in this project, see #96). */
public final class PhysicalCourseJpaMapper {

    private PhysicalCourseJpaMapper() {
    }

    public static PhysicalCourse toDomain(PhysicalCourseJpaEntity entity) {
        return new PhysicalCourse(
            CourseId.of(entity.getId()),
            entity.getTitle(),
            entity.getProfessorName(),
            DayOfWeek.valueOf(entity.getDayOfWeek()),
            entity.getStartTime(),
            PhysicalCourseLevel.valueOf(entity.getLevel()),
            entity.getCapacity(),
            entity.getStatus()
        );
    }
}
