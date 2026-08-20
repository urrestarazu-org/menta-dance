package com.menta.virtual.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualCourseJpaMapperTest {

    @Test
    void maps_every_field_and_the_precomputed_aggregates() {
        UUID id = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();
        Instant now = Instant.now();
        VirtualCourseJpaEntity entity = new VirtualCourseJpaEntity(
            id, "Tango Básico", "desc", "descripción larga", professorId, "https://cdn/img.jpg", "tango",
            "BEGINNER", true, CourseStatus.PUBLISHED, now, now
        );

        VirtualCourse course = VirtualCourseJpaMapper.toDomain(entity, 2, 5, 60);

        assertThat(course.getId().getValue()).isEqualTo(id);
        assertThat(course.getTitle()).isEqualTo("Tango Básico");
        assertThat(course.getShortDescription()).isEqualTo("desc");
        assertThat(course.getDescription()).isEqualTo("descripción larga");
        assertThat(course.getProfessorId()).isEqualTo(professorId);
        assertThat(course.getImageUrl()).isEqualTo("https://cdn/img.jpg");
        assertThat(course.getCategory().getValue()).isEqualTo("tango");
        assertThat(course.getLevel().name()).isEqualTo("BEGINNER");
        assertThat(course.isPremium()).isTrue();
        assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
        assertThat(course.getModuleCount()).isEqualTo(2);
        assertThat(course.getLessonCount()).isEqualTo(5);
        assertThat(course.getTotalDurationMinutes()).isEqualTo(60);
    }

    @Test
    void the_management_overload_defaults_aggregates_to_zero() {
        UUID id = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();
        Instant now = Instant.now();
        VirtualCourseJpaEntity entity = new VirtualCourseJpaEntity(
            id, "Tango Básico", "desc", "descripción larga", professorId, "https://cdn/img.jpg", "tango",
            "BEGINNER", true, CourseStatus.DRAFT, now, now
        );

        VirtualCourse course = VirtualCourseJpaMapper.toDomain(entity);

        assertThat(course.getModuleCount()).isZero();
        assertThat(course.getLessonCount()).isZero();
        assertThat(course.getTotalDurationMinutes()).isZero();
    }

    @Test
    void to_entity_round_trips_the_domain_fields() {
        UUID professorId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(3600);
        Instant updatedAt = Instant.now();
        VirtualCourse course = VirtualCourse.create(
            "Tango Básico", "desc", "descripción larga", professorId, "https://cdn/img.jpg",
            com.menta.virtual.domain.model.CourseCategory.of("tango"),
            com.menta.virtual.domain.model.CourseLevel.BEGINNER
        );

        VirtualCourseJpaEntity entity = VirtualCourseJpaMapper.toEntity(course, createdAt, updatedAt);

        assertThat(entity.getId()).isEqualTo(course.getId().getValue());
        assertThat(entity.getTitle()).isEqualTo("Tango Básico");
        assertThat(entity.getDescription()).isEqualTo("descripción larga");
        assertThat(entity.getProfessorId()).isEqualTo(professorId);
        assertThat(entity.getStatus()).isEqualTo(CourseStatus.DRAFT);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
