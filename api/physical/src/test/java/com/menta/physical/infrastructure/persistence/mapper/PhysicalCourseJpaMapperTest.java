package com.menta.physical.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
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
        UUID professorId = UUID.randomUUID();
        Instant now = Instant.now();
        PhysicalCourseJpaEntity entity = new PhysicalCourseJpaEntity(
            id, "Salsa inicial", "Curso de salsa", professorId, "María García", "TUESDAY", LocalTime.of(19, 0),
            60, "BEGINNER", 20, CourseStatus.ACTIVE, now, now
        );

        PhysicalCourse course = PhysicalCourseJpaMapper.toDomain(entity);

        assertThat(course.getId().getValue()).isEqualTo(id);
        assertThat(course.getTitle()).isEqualTo("Salsa inicial");
        assertThat(course.getDescription()).isEqualTo("Curso de salsa");
        assertThat(course.getProfessorId()).isEqualTo(professorId);
        assertThat(course.getProfessorName()).isEqualTo("María García");
        assertThat(course.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(course.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(course.getDurationMinutes()).isEqualTo(60);
        assertThat(course.getLevel().name()).isEqualTo("BEGINNER");
        assertThat(course.getCapacity()).isEqualTo(20);
        assertThat(course.getStatus()).isEqualTo(CourseStatus.ACTIVE);
    }

    @Test
    void maps_every_field_to_entity_preserving_supplied_timestamps() {
        UUID professorId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(3600);
        Instant updatedAt = Instant.now();
        PhysicalCourse course = PhysicalCourse.create(
            "Salsa inicial", "Curso de salsa", professorId, "María García", DayOfWeek.TUESDAY,
            LocalTime.of(19, 0), 60, PhysicalCourseLevel.BEGINNER, 20
        );

        PhysicalCourseJpaEntity entity = PhysicalCourseJpaMapper.toEntity(course, createdAt, updatedAt);

        assertThat(entity.getId()).isEqualTo(course.getId().getValue());
        assertThat(entity.getTitle()).isEqualTo("Salsa inicial");
        assertThat(entity.getDescription()).isEqualTo("Curso de salsa");
        assertThat(entity.getProfessorId()).isEqualTo(professorId);
        assertThat(entity.getProfessorName()).isEqualTo("María García");
        assertThat(entity.getDayOfWeek()).isEqualTo("TUESDAY");
        assertThat(entity.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(entity.getDurationMinutes()).isEqualTo(60);
        assertThat(entity.getLevel()).isEqualTo("BEGINNER");
        assertThat(entity.getCapacity()).isEqualTo(20);
        assertThat(entity.getStatus()).isEqualTo(CourseStatus.ACTIVE);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
