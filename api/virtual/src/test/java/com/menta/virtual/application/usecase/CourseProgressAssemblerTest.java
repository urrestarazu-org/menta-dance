package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.virtual.application.dto.CourseProgressView;
import com.menta.virtual.application.port.out.CourseProgressRowProjection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests (US-VIRTUAL-005, Slice 3): {@link CourseProgressAssembler} never sorts or
 * compares timestamps itself — it trusts the row order the repository query already produced
 * (design.md "Course aggregate: two bounded queries, no N+1").
 */
class CourseProgressAssemblerTest {

    @Test
    void zero_lesson_course_returns_zero_percentage_and_no_resume() {
        CourseProgressView view = CourseProgressAssembler.assemble("course-1", List.of(), 0);

        assertThat(view.completedLessons()).isZero();
        assertThat(view.totalLessons()).isZero();
        assertThat(view.percentage()).isZero();
        assertThat(view.resumeLesson()).isNull();
    }

    @Test
    void all_complete_lessons_yield_full_percentage_and_the_first_row_as_resume() {
        Row first = row(true, Instant.parse("2026-01-02T00:00:00Z"), 1, 1);
        Row second = row(true, Instant.parse("2026-01-01T00:00:00Z"), 2, 1);

        CourseProgressView view = CourseProgressAssembler.assemble("course-1", List.of(first, second), 2);

        assertThat(view.completedLessons()).isEqualTo(2);
        assertThat(view.percentage()).isEqualTo(100);
        assertThat(view.resumeLesson().lessonId()).isEqualTo(first.lessonId().toString());
    }

    @Test
    void rounds_completion_percentage_half_up() {
        Row a = row(true, Instant.now(), 1, 1);
        List<CourseProgressRowProjection> rows = List.of(
            a, row(false, null, 2, 1), row(false, null, 3, 1), row(false, null, 4, 1),
            row(true, Instant.now(), 5, 1), row(false, null, 6, 1), row(false, null, 7, 1), row(false, null, 8, 1)
        );

        CourseProgressView view = CourseProgressAssembler.assemble("course-1", rows, 8);

        assertThat(view.percentage()).isEqualTo(25);
    }

    @Test
    void percentage_is_clamped_below_100_unless_every_lesson_is_complete() {
        List<CourseProgressRowProjection> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 9999; i++) {
            rows.add(row(true, Instant.now(), i, 1));
        }
        rows.add(row(false, null, 9999, 1));

        CourseProgressView view = CourseProgressAssembler.assemble("course-1", rows, 10000);

        assertThat(view.percentage()).isEqualTo(99);
    }

    @Test
    void resume_selection_trusts_incoming_order_a_null_position_updated_at_row_never_wins_when_listed_second() {
        Row nonNull = row(false, Instant.parse("2026-01-05T00:00:00Z"), 5, 2);
        Row neverPlayed = row(true, null, 2, 1);

        CourseProgressView view = CourseProgressAssembler.assemble("course-1", List.of(nonNull, neverPlayed), 2);

        assertThat(view.resumeLesson().lessonId()).isEqualTo(nonNull.lessonId().toString());
        assertThat(view.resumeLesson().completed()).isFalse();
    }

    private static Row row(boolean completed, Instant positionUpdatedAt, int lessonOrder, int moduleOrder) {
        return new Row(
            UUID.randomUUID(), UUID.randomUUID(), 42, completed, positionUpdatedAt, lessonOrder, moduleOrder
        );
    }

    private record Row(
        UUID lessonId, UUID moduleId, int positionSeconds, boolean completed, Instant positionUpdatedAt,
        int lessonOrder, int moduleOrder
    ) implements CourseProgressRowProjection {
        @Override
        public UUID getLessonId() {
            return lessonId;
        }

        @Override
        public UUID getModuleId() {
            return moduleId;
        }

        @Override
        public int getPositionSeconds() {
            return positionSeconds;
        }

        @Override
        public boolean isCompleted() {
            return completed;
        }

        @Override
        public Instant getPositionUpdatedAt() {
            return positionUpdatedAt;
        }

        @Override
        public int getLessonOrder() {
            return lessonOrder;
        }

        @Override
        public int getModuleOrder() {
            return moduleOrder;
        }
    }
}
