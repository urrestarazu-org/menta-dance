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
        Instant now = Instant.now();
        VirtualCourseJpaEntity entity = new VirtualCourseJpaEntity(
            id, "Tango Básico", "desc", "https://cdn/img.jpg", "tango", "BEGINNER",
            true, CourseStatus.PUBLISHED, now, now
        );

        VirtualCourse course = VirtualCourseJpaMapper.toDomain(entity, 2, 5, 60);

        assertThat(course.getId().getValue()).isEqualTo(id);
        assertThat(course.getTitle()).isEqualTo("Tango Básico");
        assertThat(course.getShortDescription()).isEqualTo("desc");
        assertThat(course.getImageUrl()).isEqualTo("https://cdn/img.jpg");
        assertThat(course.getCategory().getValue()).isEqualTo("tango");
        assertThat(course.getLevel().name()).isEqualTo("BEGINNER");
        assertThat(course.isPremium()).isTrue();
        assertThat(course.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
        assertThat(course.getModuleCount()).isEqualTo(2);
        assertThat(course.getLessonCount()).isEqualTo(5);
        assertThat(course.getTotalDurationMinutes()).isEqualTo(60);
    }
}
