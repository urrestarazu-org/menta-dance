package com.menta.physical.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalCourseJpaMapperTest {

    @Test
    void maps_every_field_to_domain() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        PhysicalCourseJpaEntity entity = new PhysicalCourseJpaEntity(
            id, "Salsa inicial", "María García", "TUESDAY", LocalTime.of(19, 0),
            "BEGINNER", 20, CourseStatus.ACTIVE, now, now
        );

        PhysicalCourse course = PhysicalCourseJpaMapper.toDomain(entity);

        assertThat(course.getId().getValue()).isEqualTo(id);
        assertThat(course.getTitle()).isEqualTo("Salsa inicial");
        assertThat(course.getProfessorName()).isEqualTo("María García");
        assertThat(course.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(course.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(course.getLevel().name()).isEqualTo("BEGINNER");
        assertThat(course.getCapacity()).isEqualTo(20);
        assertThat(course.getStatus()).isEqualTo(CourseStatus.ACTIVE);
    }
}
