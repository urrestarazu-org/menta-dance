package com.menta.virtual.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.LessonProgress;
import com.menta.virtual.domain.model.LessonProgressId;
import com.menta.virtual.infrastructure.persistence.entity.LessonProgressJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LessonProgressJpaMapperTest {

    @Test
    void round_trips_a_progress_row_with_a_saved_position() {
        LessonProgress progress = new LessonProgress(
            LessonProgressId.generate(), UUID.randomUUID(), LessonId.generate(), CourseId.generate(),
            300, false, null, Instant.parse("2026-01-01T00:00:00Z")
        );
        Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-01T00:00:00Z");

        LessonProgressJpaEntity entity = LessonProgressJpaMapper.toEntity(progress, createdAt, updatedAt);
        LessonProgress roundTripped = LessonProgressJpaMapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(progress.getId());
        assertThat(roundTripped.getUserId()).isEqualTo(progress.getUserId());
        assertThat(roundTripped.getLessonId()).isEqualTo(progress.getLessonId());
        assertThat(roundTripped.getCourseId()).isEqualTo(progress.getCourseId());
        assertThat(roundTripped.getPositionSeconds()).isEqualTo(300);
        assertThat(roundTripped.isCompleted()).isFalse();
        assertThat(roundTripped.getPositionUpdatedAt()).isEqualTo(progress.getPositionUpdatedAt());
    }

    @Test
    void round_trips_a_completed_never_played_row_with_a_null_position_updated_at() {
        LessonProgress progress = new LessonProgress(
            LessonProgressId.generate(), UUID.randomUUID(), LessonId.generate(), CourseId.generate(),
            0, true, Instant.parse("2026-02-01T00:00:00Z"), null
        );

        LessonProgressJpaEntity entity = LessonProgressJpaMapper.toEntity(progress, Instant.now(), Instant.now());
        LessonProgress roundTripped = LessonProgressJpaMapper.toDomain(entity);

        assertThat(roundTripped.getPositionUpdatedAt()).isNull();
        assertThat(roundTripped.isCompleted()).isTrue();
        assertThat(roundTripped.getCompletedAt()).isEqualTo(progress.getCompletedAt());
    }
}
