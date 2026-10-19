package com.menta.physical.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.physical.domain.model.CourseStatus;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalCourseJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant updatedAt = createdAt.plusSeconds(60);

        PhysicalCourseJpaEntity entity = new PhysicalCourseJpaEntity(
            id, "Salsa inicial", "Curso de salsa", professorId, "María García", "TUESDAY", LocalTime.of(19, 0),
            60, "BEGINNER", 20, CourseStatus.ACTIVE, createdAt, updatedAt
        );

        assertThat(entity.getId()).isEqualTo(id);
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
