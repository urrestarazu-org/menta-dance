package com.menta.virtual.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.virtual.domain.exception.InvalidLessonPositionException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LessonProgressTest {

    private static final int MAX_SECONDS = 600;

    private static LessonProgress freshProgress() {
        return LessonProgress.start(UUID.randomUUID(), LessonId.generate(), CourseId.generate());
    }

    @Test
    void position_within_bounds_is_accepted() {
        LessonProgress progress = freshProgress().withPosition(300, MAX_SECONDS, Instant.now());

        assertThat(progress.getPositionSeconds()).isEqualTo(300);
    }

    @Test
    void negative_position_is_rejected() {
        LessonProgress progress = freshProgress();

        assertThatThrownBy(() -> progress.withPosition(-1, MAX_SECONDS, Instant.now()))
            .isInstanceOf(InvalidLessonPositionException.class);
    }

    @Test
    void position_exceeding_the_bound_is_rejected() {
        LessonProgress progress = freshProgress();

        assertThatThrownBy(() -> progress.withPosition(MAX_SECONDS + 1, MAX_SECONDS, Instant.now()))
            .isInstanceOf(InvalidLessonPositionException.class);
    }

    @Test
    void marking_completed_sets_completed_and_completed_at_without_touching_position() {
        Instant savedAt = Instant.now().minusSeconds(60);
        LessonProgress saved = freshProgress().withPosition(120, MAX_SECONDS, savedAt);
        Instant completedAt = Instant.now();

        LessonProgress completed = saved.markCompleted(completedAt);

        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.getCompletedAt()).isEqualTo(completedAt);
        assertThat(completed.getPositionSeconds()).isEqualTo(120);
        assertThat(completed.getPositionUpdatedAt()).isEqualTo(savedAt);
    }

    @Test
    void marking_completed_twice_is_a_no_op() {
        LessonProgress completed = freshProgress().markCompleted(Instant.now());

        LessonProgress completedAgain = completed.markCompleted(Instant.now().plusSeconds(10));

        assertThat(completedAgain).isSameAs(completed);
    }
}
