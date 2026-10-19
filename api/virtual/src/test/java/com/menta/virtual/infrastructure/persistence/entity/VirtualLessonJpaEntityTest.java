package com.menta.virtual.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualLessonJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        VirtualLessonJpaEntity entity = new VirtualLessonJpaEntity(
            id, moduleId, courseId, "Lección 1", "desc", "video-123", 15, true, 3
        );

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getModuleId()).isEqualTo(moduleId);
        assertThat(entity.getCourseId()).isEqualTo(courseId);
        assertThat(entity.getTitle()).isEqualTo("Lección 1");
        assertThat(entity.getDescription()).isEqualTo("desc");
        assertThat(entity.getVideoId()).isEqualTo("video-123");
        assertThat(entity.getDurationMinutes()).isEqualTo(15);
        assertThat(entity.isFree()).isTrue();
        assertThat(entity.getDisplayOrder()).isEqualTo(3);
    }
}
