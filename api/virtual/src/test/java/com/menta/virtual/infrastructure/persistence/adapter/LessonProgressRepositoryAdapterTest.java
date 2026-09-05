package com.menta.virtual.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.LessonProgress;
import com.menta.virtual.domain.model.LessonProgressId;
import com.menta.virtual.infrastructure.persistence.entity.LessonProgressJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.LessonProgressJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LessonProgressRepositoryAdapterTest {

    private final LessonProgressJpaRepository jpaRepository = mock(LessonProgressJpaRepository.class);
    private final LessonProgressRepositoryAdapter adapter = new LessonProgressRepositoryAdapter(jpaRepository);

    private static LessonProgressJpaEntity entity(UUID id, UUID userId, UUID lessonId, UUID courseId) {
        return new LessonProgressJpaEntity(
            id, userId, lessonId, courseId, 120, false, null, Instant.now(), null, Instant.now()
        );
    }

    @Test
    void find_by_user_id_and_lesson_id_maps_the_entity() {
        UUID userId = UUID.randomUUID();
        UUID lessonUuid = UUID.randomUUID();
        when(jpaRepository.findByUserIdAndLessonId(userId, lessonUuid))
            .thenReturn(Optional.of(entity(UUID.randomUUID(), userId, lessonUuid, UUID.randomUUID())));

        assertThat(adapter.findByUserIdAndLessonId(userId, LessonId.of(lessonUuid))).isPresent();
    }

    @Test
    void find_by_user_id_and_lesson_id_returns_empty_when_missing() {
        UUID userId = UUID.randomUUID();
        UUID lessonUuid = UUID.randomUUID();
        when(jpaRepository.findByUserIdAndLessonId(userId, lessonUuid)).thenReturn(Optional.empty());

        assertThat(adapter.findByUserIdAndLessonId(userId, LessonId.of(lessonUuid))).isEmpty();
    }

    @Test
    void save_reuses_the_existing_rows_created_at() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID lessonUuid = UUID.randomUUID();
        UUID courseUuid = UUID.randomUUID();
        Instant originalCreatedAt = Instant.parse("2020-01-01T00:00:00Z");
        when(jpaRepository.findById(id)).thenReturn(Optional.of(
            new LessonProgressJpaEntity(
                id, userId, lessonUuid, courseUuid, 0, false, null, originalCreatedAt, null, originalCreatedAt
            )
        ));
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LessonProgress progress = new LessonProgress(
            LessonProgressId.of(id), userId, LessonId.of(lessonUuid), CourseId.of(courseUuid),
            300, false, null, Instant.now()
        );

        LessonProgress saved = adapter.save(progress);

        assertThat(saved.getPositionSeconds()).isEqualTo(300);
    }

    @Test
    void save_defaults_created_at_to_now_for_a_brand_new_row() {
        LessonProgress progress = LessonProgress.start(UUID.randomUUID(), LessonId.generate(), CourseId.generate());
        when(jpaRepository.findById(progress.getId().getValue())).thenReturn(Optional.empty());
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgress saved = adapter.save(progress);

        assertThat(saved.getPositionSeconds()).isZero();
    }
}
