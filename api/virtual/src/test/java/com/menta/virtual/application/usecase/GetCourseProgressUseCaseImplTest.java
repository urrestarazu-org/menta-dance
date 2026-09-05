package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.shared.billing.CourseAccessSnapshot;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.application.dto.CourseProgressView;
import com.menta.virtual.application.port.out.CourseProgressRowProjection;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.exception.ForbiddenCourseProgressException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Mockito-only (US-VIRTUAL-005, Slice 3): proves the use case performs exactly the two bounded
 * repository reads design.md requires (count + row projection) once the course resolves as
 * PUBLISHED and the caller is entitled — never before.
 */
class GetCourseProgressUseCaseImplTest {

    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final LessonProgressRepository progressRepository = mock(LessonProgressRepository.class);
    private final VirtualCourseEntitlementPort entitlementPort = mock(VirtualCourseEntitlementPort.class);
    private final GetCourseProgressUseCaseImpl useCase = new GetCourseProgressUseCaseImpl(
        courseRepository, progressRepository, new CourseProgressAccessPolicy(entitlementPort)
    );

    private static VirtualCourse publishedCourse(CourseId id) {
        return new VirtualCourse(
            id, "Tango Básico", "Aprendé los pasos fundamentales", "Descripción larga", UUID.randomUUID(),
            "https://cdn/tango.jpg", CourseCategory.of("tango"), CourseLevel.BEGINNER, true,
            CourseStatus.PUBLISHED, 1, 4, 60
        );
    }

    /**
     * A plain record, not a Mockito mock: stubbing a mock as an argument expression to another
     * {@code when(...).thenReturn(...)} call confuses Mockito's ongoing-stubbing state (same
     * pitfall {@link CourseProgressAssemblerTest} avoids with its own {@code Row} record).
     */
    private static CourseProgressRowProjection row(boolean completed, Instant positionUpdatedAt) {
        return new Row(UUID.randomUUID(), UUID.randomUUID(), 42, completed, positionUpdatedAt, 0, 0);
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

    @Test
    void entitled_student_receives_the_assembled_view_from_exactly_two_bounded_reads() {
        CourseId courseId = CourseId.generate();
        UUID userId = UUID.randomUUID();
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(publishedCourse(courseId)));
        when(entitlementPort.resolveCourseAccess(userId, courseId.getValue().toString()))
            .thenReturn(new CourseAccessSnapshot(true, true));
        List<CourseProgressRowProjection> rows = List.of(row(true, Instant.now()));
        when(progressRepository.countLessonsByCourseId(courseId.getValue())).thenReturn(4L);
        when(progressRepository.findRowsForUserAndCourse(userId, courseId.getValue())).thenReturn(rows);

        CourseProgressView view = useCase.get(courseId.toString(), userId);

        assertThat(view.completedLessons()).isEqualTo(1);
        assertThat(view.totalLessons()).isEqualTo(4);
        verify(progressRepository, times(1)).countLessonsByCourseId(courseId.getValue());
        verify(progressRepository, times(1)).findRowsForUserAndCourse(userId, courseId.getValue());
    }

    @Test
    void zero_lesson_course_returns_a_zeroed_view_never_a_not_found_signal() {
        CourseId courseId = CourseId.generate();
        UUID userId = UUID.randomUUID();
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(publishedCourse(courseId)));
        when(entitlementPort.resolveCourseAccess(userId, courseId.getValue().toString()))
            .thenReturn(new CourseAccessSnapshot(true, true));
        when(progressRepository.countLessonsByCourseId(courseId.getValue())).thenReturn(0L);
        when(progressRepository.findRowsForUserAndCourse(userId, courseId.getValue())).thenReturn(List.of());

        CourseProgressView view = useCase.get(courseId.toString(), userId);

        assertThat(view.totalLessons()).isZero();
        assertThat(view.completedLessons()).isZero();
        assertThat(view.percentage()).isZero();
        assertThat(view.resumeLesson()).isNull();
    }

    @Test
    void a_completed_lesson_that_later_became_inaccessible_still_counts() {
        CourseId courseId = CourseId.generate();
        UUID userId = UUID.randomUUID();
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(publishedCourse(courseId)));
        when(entitlementPort.resolveCourseAccess(userId, courseId.getValue().toString()))
            .thenReturn(new CourseAccessSnapshot(true, true));
        // The row is returned regardless of the current per-lesson accessibility of that lesson —
        // this use case never re-checks lesson-level access once course-level entitlement is granted.
        when(progressRepository.countLessonsByCourseId(courseId.getValue())).thenReturn(1L);
        when(progressRepository.findRowsForUserAndCourse(userId, courseId.getValue()))
            .thenReturn(List.of(row(true, Instant.now())));

        CourseProgressView view = useCase.get(courseId.toString(), userId);

        assertThat(view.completedLessons()).isEqualTo(1);
        assertThat(view.percentage()).isEqualTo(100);
    }

    @Test
    void no_entitlement_is_denied_before_any_progress_read() {
        CourseId courseId = CourseId.generate();
        UUID userId = UUID.randomUUID();
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(publishedCourse(courseId)));
        when(entitlementPort.resolveCourseAccess(userId, courseId.getValue().toString()))
            .thenReturn(new CourseAccessSnapshot(true, false));

        assertThatThrownBy(() -> useCase.get(courseId.toString(), userId))
            .isInstanceOf(ForbiddenCourseProgressException.class);
        verify(progressRepository, never()).countLessonsByCourseId(courseId.getValue());
        verify(progressRepository, never()).findRowsForUserAndCourse(userId, courseId.getValue());
    }

    @Test
    void unknown_course_throws_not_found_before_the_entitlement_check() {
        CourseId courseId = CourseId.generate();
        UUID userId = UUID.randomUUID();
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(courseId.toString(), userId))
            .isInstanceOf(CourseNotFoundException.class);
        verify(entitlementPort, never()).resolveCourseAccess(any(), anyString());
    }

    @Test
    void malformed_course_id_throws_not_found() {
        assertThatThrownBy(() -> useCase.get("not-a-uuid", UUID.randomUUID()))
            .isInstanceOf(CourseNotFoundException.class);
    }
}
