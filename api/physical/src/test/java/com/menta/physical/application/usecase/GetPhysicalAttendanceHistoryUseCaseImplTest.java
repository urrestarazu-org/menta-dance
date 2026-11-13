package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.AttendanceHistoryRow;
import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceViewer;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.domain.exception.InvalidSessionRangeException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * #39, US-PHYSICAL-002, design C2/C4, #729. The range is UTC and goes to the repository untouched,
 * whichever viewer reads: there is no zone to resolve and no month to derive.
 */
class GetPhysicalAttendanceHistoryUseCaseImplTest {

    // The local month of November 2026 in Buenos Aires, as a client would send it.
    private static final Instant FROM = Instant.parse("2026-11-01T03:00:00Z");
    private static final Instant TO = Instant.parse("2026-12-01T03:00:00Z");

    private final PhysicalCapacityAssignmentRepository repository = mock(PhysicalCapacityAssignmentRepository.class);
    private final GetPhysicalAttendanceHistoryUseCaseImpl useCase =
        new GetPhysicalAttendanceHistoryUseCaseImpl(repository);

    private static AttendanceHistoryRow row(Instant scheduledAt) {
        return new AttendanceHistoryRow(UUID.randomUUID(), scheduledAt, "Salsa", "Ana", null, null);
    }

    @Test
    void the_given_bounds_reach_the_repository_untouched_and_are_echoed_in_the_view() {
        when(repository.findAttendance(any(), any(), any())).thenReturn(List.of());
        UUID studentId = UUID.randomUUID();

        AttendanceHistoryView view = useCase.history(AttendanceViewer.self(studentId), FROM, TO, true);

        verify(repository).findAttendance(studentId, FROM, TO);
        assertThat(view.from()).isEqualTo(FROM);
        assertThat(view.to()).isEqualTo(TO);
    }

    /**
     * The half-open contract: the bounds are forwarded as given, so the query's {@code >= from AND < to}
     * keeps a session exactly at {@code from} and drops one exactly at {@code to}; the use case itself
     * keeps every row the repository returns.
     */
    @Test
    void a_session_exactly_at_from_is_kept_and_the_bounds_are_the_ones_that_exclude_to() {
        AttendanceHistoryRow atFrom = row(FROM);
        when(repository.findAttendance(any(), any(), any())).thenReturn(List.of(atFrom));
        UUID studentId = UUID.randomUUID();

        AttendanceHistoryView view = useCase.history(AttendanceViewer.self(studentId), FROM, TO, true);

        assertThat(view.scheduledSessionCount()).isEqualTo(1);
        assertThat(view.sessions()).singleElement().extracting(session -> session.scheduledAt()).isEqualTo(FROM);
        verify(repository).findAttendance(studentId, FROM, TO);
    }

    @Test
    void self_and_admin_viewers_both_dispatch_to_the_unrestricted_query_and_never_reach_the_scoped_method() {
        when(repository.findAttendance(any(), any(), any())).thenReturn(List.of());
        UUID studentId = UUID.randomUUID();

        useCase.history(AttendanceViewer.self(studentId), FROM, TO, true);
        useCase.history(AttendanceViewer.elevated(studentId, UUID.randomUUID(), true), FROM, TO, true);

        verify(repository, times(2)).findAttendance(studentId, FROM, TO);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void a_student_with_zero_rows_in_the_range_yields_the_empty_shape_echoing_the_range() {
        when(repository.findAttendance(any(), any(), any())).thenReturn(List.of());

        AttendanceHistoryView view = useCase.history(AttendanceViewer.self(UUID.randomUUID()), FROM, TO, true);

        assertThat(view).isEqualTo(new AttendanceHistoryView(FROM, TO, 0, 0, 0, new BigDecimal("0.00"), List.of()));
    }

    /**
     * #39, US-PHYSICAL-002 P2, design C2/R7: {@code InstructorOwnCourses} MUST call the scoped method
     * with the caller's own id, never {@code null}, and MUST NOT reach the unrestricted method at all.
     */
    @Test
    void instructor_own_courses_viewer_dispatches_to_the_scoped_method_with_the_callers_own_id() {
        when(repository.findAttendanceInCoursesOwnedBy(any(), any(), any(), any())).thenReturn(List.of());
        UUID studentId = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();

        useCase.history(AttendanceViewer.elevated(studentId, professorId, false), FROM, TO, true);

        verify(repository).findAttendanceInCoursesOwnedBy(studentId, FROM, TO, professorId);
        verifyNoMoreInteractions(repository);
    }

    /**
     * #39, US-PHYSICAL-002 P2, design C4/R9 — anti-enumeration equality, instructor case: an
     * {@code InstructorOwnCourses} viewer for a student with zero overlapping courses yields the same
     * empty shape as any other empty read. No existence port exists to distinguish "student not found"
     * from "student exists but no course overlap" from "student exists, zero sessions in the range".
     */
    @Test
    void an_instructor_viewer_with_zero_overlapping_courses_yields_the_same_empty_shape() {
        when(repository.findAttendanceInCoursesOwnedBy(any(), any(), any(), any())).thenReturn(List.of());

        AttendanceHistoryView view = useCase.history(
            AttendanceViewer.elevated(UUID.randomUUID(), UUID.randomUUID(), false), FROM, TO, true
        );

        assertThat(view).isEqualTo(new AttendanceHistoryView(FROM, TO, 0, 0, 0, new BigDecimal("0.00"), List.of()));
    }

    @Test
    void a_range_of_exactly_92_days_is_accepted_by_every_viewer() {
        when(repository.findAttendance(any(), any(), any())).thenReturn(List.of());
        when(repository.findAttendanceInCoursesOwnedBy(any(), any(), any(), any())).thenReturn(List.of());
        UUID studentId = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();
        Instant to = FROM.plus(Duration.ofDays(92));

        useCase.history(AttendanceViewer.self(studentId), FROM, to, true);
        useCase.history(AttendanceViewer.elevated(studentId, professorId, false), FROM, to, true);

        verify(repository).findAttendance(studentId, FROM, to);
        verify(repository).findAttendanceInCoursesOwnedBy(studentId, FROM, to, professorId);
    }

    @Test
    void a_range_of_92_days_and_one_nanosecond_is_rejected_without_reading() {
        Instant to = FROM.plus(Duration.ofDays(92)).plusNanos(1);

        assertThatThrownBy(() -> useCase.history(AttendanceViewer.self(UUID.randomUUID()), FROM, to, true))
            .isInstanceOf(InvalidSessionRangeException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void an_empty_range_is_rejected_without_reading() {
        assertThatThrownBy(() -> useCase.history(AttendanceViewer.self(UUID.randomUUID()), FROM, FROM, true))
            .isInstanceOf(InvalidSessionRangeException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void an_inverted_range_is_rejected_without_reading() {
        AttendanceViewer viewer = AttendanceViewer.elevated(UUID.randomUUID(), UUID.randomUUID(), false);

        assertThatThrownBy(() -> useCase.history(viewer, TO, FROM, true))
            .isInstanceOf(InvalidSessionRangeException.class);

        verifyNoInteractions(repository);
    }
}
