package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.shared.billing.CourseAccessSnapshot;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.application.dto.InProgressCourseView;
import com.menta.virtual.application.port.in.ListInProgressCoursesUseCase;
import com.menta.virtual.application.port.out.InProgressCourseRowProjection;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.domain.exception.InvalidInProgressCoursesLimitException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Mockito-only (#765): two bounded repository reads whatever the number of courses, the same
 * access rule as the per-course progress (through the real {@link CourseProgressAccessPolicy}),
 * and the percentage/resume of {@link CourseProgressAssembler}.
 */
class ListInProgressCoursesUseCaseImplTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    private final LessonProgressRepository progressRepository = mock(LessonProgressRepository.class);
    private final VirtualCourseEntitlementPort entitlementPort = mock(VirtualCourseEntitlementPort.class);
    private final ListInProgressCoursesUseCaseImpl useCase = new ListInProgressCoursesUseCaseImpl(
        progressRepository, new CourseProgressAccessPolicy(entitlementPort)
    );
    private final UUID userId = UUID.randomUUID();

    private record Row(
        UUID courseId, String courseTitle, UUID lessonId, String lessonTitle, UUID moduleId,
        int positionSeconds, boolean completed, Instant positionUpdatedAt
    ) implements InProgressCourseRowProjection {
        @Override
        public UUID getCourseId() {
            return courseId;
        }

        @Override
        public String getCourseTitle() {
            return courseTitle;
        }

        @Override
        public String getLessonTitle() {
            return lessonTitle;
        }

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
            return 0;
        }

        @Override
        public int getModuleOrder() {
            return 0;
        }
    }

    private static Row row(UUID courseId, String lessonTitle, int position, boolean completed, Instant updatedAt) {
        return new Row(
            courseId, "Curso " + courseId, UUID.randomUUID(), lessonTitle, UUID.randomUUID(), position, completed,
            updatedAt
        );
    }

    private void grant(UUID courseId, boolean granted) {
        when(entitlementPort.resolveCourseAccess(userId, courseId.toString()))
            .thenReturn(new CourseAccessSnapshot(true, granted));
    }

    @Test
    void no_progress_returns_an_empty_list_without_counting_lessons_or_asking_billing() {
        when(progressRepository.findRowsForUserInPublishedCourses(userId)).thenReturn(List.of());

        assertThat(useCase.list(userId, 10)).isEmpty();
        verify(progressRepository, never()).countLessonsByCourseIds(anyCollection());
        verifyNoInteractions(entitlementPort);
    }

    @Test
    void lists_in_progress_courses_in_the_incoming_recency_order_with_titles_and_resume_lesson() {
        UUID recent = UUID.randomUUID();
        UUID older = UUID.randomUUID();
        Row recentResume = row(recent, "Giros", 95, false, T0.plusSeconds(600));
        Row olderResume = row(older, "Postura", 30, false, T0.plusSeconds(60));
        Row olderCompleted = row(older, "Abrazo", 0, true, T0);
        when(progressRepository.findRowsForUserInPublishedCourses(userId))
            .thenReturn(List.of(recentResume, olderResume, olderCompleted));
        when(progressRepository.countLessonsByCourseIds(Set.of(recent, older)))
            .thenReturn(Map.of(recent, 4L, older, 3L));
        grant(recent, true);
        grant(older, true);

        List<InProgressCourseView> courses = useCase.list(userId, 10);

        assertThat(courses).extracting(InProgressCourseView::courseId)
            .containsExactly(recent.toString(), older.toString());
        InProgressCourseView first = courses.get(0);
        assertThat(first.courseTitle()).isEqualTo("Curso " + recent);
        assertThat(first.completedLessons()).isZero();
        assertThat(first.totalLessons()).isEqualTo(4);
        assertThat(first.percentage()).isZero();
        assertThat(first.resumeLesson()).isEqualTo(new InProgressCourseView.ResumeLesson(
            recentResume.lessonId().toString(), recentResume.moduleId().toString(), "Giros", 95
        ));
        InProgressCourseView second = courses.get(1);
        assertThat(second.completedLessons()).isEqualTo(1);
        assertThat(second.totalLessons()).isEqualTo(3);
        assertThat(second.percentage()).isEqualTo(33);
        assertThat(second.resumeLesson().lessonId()).isEqualTo(olderResume.lessonId().toString());
        assertThat(second.resumeLesson().lessonTitle()).isEqualTo("Postura");
        verify(progressRepository, times(1)).findRowsForUserInPublishedCourses(userId);
        verify(progressRepository, times(1)).countLessonsByCourseIds(anyCollection());
    }

    @Test
    void a_completed_course_is_excluded_without_asking_billing_about_it() {
        UUID completed = UUID.randomUUID();
        UUID inProgress = UUID.randomUUID();
        when(progressRepository.findRowsForUserInPublishedCourses(userId)).thenReturn(List.of(
            row(completed, "Final", 0, true, T0.plusSeconds(900)),
            row(completed, "Inicio", 0, true, T0.plusSeconds(800)),
            row(inProgress, "Medio", 10, false, T0)
        ));
        when(progressRepository.countLessonsByCourseIds(any())).thenReturn(Map.of(completed, 2L, inProgress, 2L));
        grant(inProgress, true);

        List<InProgressCourseView> courses = useCase.list(userId, 10);

        assertThat(courses).extracting(InProgressCourseView::courseId).containsExactly(inProgress.toString());
        verify(entitlementPort, never()).resolveCourseAccess(userId, completed.toString());
    }

    @Test
    void a_course_without_current_access_is_left_out_instead_of_failing() {
        UUID lost = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        when(progressRepository.findRowsForUserInPublishedCourses(userId)).thenReturn(List.of(
            row(lost, "Perdida", 10, false, T0.plusSeconds(60)),
            row(kept, "Vigente", 10, false, T0)
        ));
        when(progressRepository.countLessonsByCourseIds(any())).thenReturn(Map.of(lost, 2L, kept, 2L));
        grant(lost, false);
        grant(kept, true);

        assertThat(useCase.list(userId, 10)).extracting(InProgressCourseView::courseId)
            .containsExactly(kept.toString());
    }

    @Test
    void an_unavailable_entitlement_check_fails_closed_and_leaves_the_course_out() {
        UUID courseId = UUID.randomUUID();
        when(progressRepository.findRowsForUserInPublishedCourses(userId))
            .thenReturn(List.of(row(courseId, "Lección", 10, false, T0)));
        when(progressRepository.countLessonsByCourseIds(any())).thenReturn(Map.of(courseId, 2L));
        when(entitlementPort.resolveCourseAccess(any(), anyString())).thenThrow(new IllegalStateException("down"));

        assertThat(useCase.list(userId, 10)).isEmpty();
    }

    @Test
    void stops_at_the_limit_and_never_asks_billing_about_the_remaining_courses() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        when(progressRepository.findRowsForUserInPublishedCourses(userId)).thenReturn(List.of(
            row(first, "A", 1, false, T0.plusSeconds(3)),
            row(second, "B", 1, false, T0.plusSeconds(2)),
            row(third, "C", 1, false, T0.plusSeconds(1))
        ));
        when(progressRepository.countLessonsByCourseIds(any())).thenReturn(Map.of(first, 2L, second, 2L, third, 2L));
        grant(first, true);
        grant(second, true);

        assertThat(useCase.list(userId, 2)).extracting(InProgressCourseView::courseId)
            .containsExactly(first.toString(), second.toString());
        verify(entitlementPort, never()).resolveCourseAccess(userId, third.toString());
    }

    @Test
    void a_course_left_out_for_lack_of_access_does_not_use_up_the_limit() {
        UUID lost = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        when(progressRepository.findRowsForUserInPublishedCourses(userId)).thenReturn(List.of(
            row(lost, "A", 1, false, T0.plusSeconds(2)),
            row(kept, "B", 1, false, T0.plusSeconds(1))
        ));
        when(progressRepository.countLessonsByCourseIds(any())).thenReturn(Map.of(lost, 2L, kept, 2L));
        grant(lost, false);
        grant(kept, true);

        assertThat(useCase.list(userId, 1)).extracting(InProgressCourseView::courseId)
            .containsExactly(kept.toString());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, ListInProgressCoursesUseCase.MAX_LIMIT + 1})
    void a_limit_outside_the_allowed_range_is_rejected_before_any_read(int limit) {
        assertThatThrownBy(() -> useCase.list(userId, limit))
            .isInstanceOf(InvalidInProgressCoursesLimitException.class)
            .extracting("errorCode").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(progressRepository, entitlementPort);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, ListInProgressCoursesUseCase.MAX_LIMIT})
    void the_limit_bounds_are_inclusive(int limit) {
        when(progressRepository.findRowsForUserInPublishedCourses(userId)).thenReturn(List.of());

        assertThat(useCase.list(userId, limit)).isEmpty();
    }
}
