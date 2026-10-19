package com.menta.virtual.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualLessonJpaMapperTest {

    @Test
    void round_trips_every_field() {
        UUID id = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        VirtualLessonJpaEntity entity = new VirtualLessonJpaEntity(
            id, moduleId, courseId, "Lección 1", "desc", "video-1", 12, true, 2
        );

        VirtualLesson domain = VirtualLessonJpaMapper.toDomain(entity);

        assertThat(domain.getId().getValue()).isEqualTo(id);
        assertThat(domain.getModuleId().getValue()).isEqualTo(moduleId);
        assertThat(domain.getCourseId().getValue()).isEqualTo(courseId);
        assertThat(domain.getVideoId()).isEqualTo("video-1");
        assertThat(domain.isFree()).isTrue();
        assertThat(domain.getOrder()).isEqualTo(2);

        VirtualLessonJpaEntity roundTripped = VirtualLessonJpaMapper.toEntity(domain);
        assertThat(roundTripped.getId()).isEqualTo(id);
        assertThat(roundTripped.getDurationMinutes()).isEqualTo(12);
        assertThat(roundTripped.isFree()).isTrue();
    }
}
