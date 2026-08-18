package com.menta.virtual.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.virtual.domain.model.CourseStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualCourseJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant updatedAt = createdAt.plusSeconds(60);

        VirtualCourseJpaEntity entity = new VirtualCourseJpaEntity(
            id, "Tango Básico", "desc", "https://cdn/img.jpg", "tango", "BEGINNER",
            true, CourseStatus.PUBLISHED, createdAt, updatedAt
        );

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getTitle()).isEqualTo("Tango Básico");
        assertThat(entity.getShortDescription()).isEqualTo("desc");
        assertThat(entity.getImageUrl()).isEqualTo("https://cdn/img.jpg");
        assertThat(entity.getCategory()).isEqualTo("tango");
        assertThat(entity.getLevel()).isEqualTo("BEGINNER");
        assertThat(entity.isPremium()).isTrue();
        assertThat(entity.getStatus()).isEqualTo(CourseStatus.PUBLISHED);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
