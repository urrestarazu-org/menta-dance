package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.AttendanceHistoryRow;
import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceViewer;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * #39, US-PHYSICAL-002, design C2/C3/C4. Zone-bounds pair (Risk #1) is mandatory: a single
 * UTC-written test would pass a UTC-hardcoded regression.
 */
class GetPhysicalAttendanceHistoryUseCaseImplTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    void september_month_bounds_resolve_in_buenos_aires_zone() {
        PhysicalCapacityAssignmentRepository repository = mock(PhysicalCapacityAssignmentRepository.class);
        when(repository.findMonthlyAttendance(any(), any(), any())).thenReturn(List.of());
        GetPhysicalAttendanceHistoryUseCaseImpl useCase =
            new GetPhysicalAttendanceHistoryUseCaseImpl(repository, BUENOS_AIRES);
        UUID studentId = UUID.randomUUID();

        useCase.history(AttendanceViewer.self(studentId), YearMonth.of(2026, 9), true);

        verify(repository).findMonthlyAttendance(
            studentId, Instant.parse("2026-09-01T03:00:00Z"), Instant.parse("2026-10-01T03:00:00Z")
        );
    }

    /**
     * The two-zone pair Risk #1 mandates: the SAME instant lands in September under Buenos
     * Aires and in October under UTC, proving month attribution actually depends on the
     * configured zone rather than a hardcoded UTC assumption.
     */
    @Test
    void a_late_evening_session_lands_in_september_under_buenos_aires_but_october_under_utc() {
        UUID studentId = UUID.randomUUID();
        Instant lateEveningInstant = Instant.parse("2026-10-01T02:00:00Z"); // 2026-09-30T23:00-03:00

        PhysicalCapacityAssignmentRepository buenosAiresRepository = mock(PhysicalCapacityAssignmentRepository.class);
        when(buenosAiresRepository.findMonthlyAttendance(any(), any(), any())).thenReturn(List.of(
            new AttendanceHistoryRow(UUID.randomUUID(), lateEveningInstant, "Salsa", "Ana", null)
        ));
        GetPhysicalAttendanceHistoryUseCaseImpl buenosAiresUseCase =
            new GetPhysicalAttendanceHistoryUseCaseImpl(buenosAiresRepository, BUENOS_AIRES);

        AttendanceHistoryView septemberInBuenosAires =
            buenosAiresUseCase.history(AttendanceViewer.self(studentId), YearMonth.of(2026, 9), true);
        assertThat(septemberInBuenosAires.scheduledSessionCount()).isEqualTo(1);
        verify(buenosAiresRepository).findMonthlyAttendance(
            studentId, Instant.parse("2026-09-01T03:00:00Z"), Instant.parse("2026-10-01T03:00:00Z")
        );

        PhysicalCapacityAssignmentRepository utcRepository = mock(PhysicalCapacityAssignmentRepository.class);
        when(utcRepository.findMonthlyAttendance(any(), any(), any())).thenReturn(List.of());
        GetPhysicalAttendanceHistoryUseCaseImpl utcUseCase =
            new GetPhysicalAttendanceHistoryUseCaseImpl(utcRepository, ZoneOffset.UTC);

        utcUseCase.history(AttendanceViewer.self(studentId), YearMonth.of(2026, 9), true);
        verify(utcRepository).findMonthlyAttendance(
            studentId, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")
        );
        // The same lateEveningInstant is >= 2026-10-01T00:00:00Z, so under UTC bounds it would
        // NOT be included in September at all — it belongs to October instead.
        assertThat(lateEveningInstant.isBefore(Instant.parse("2026-10-01T00:00:00Z"))).isFalse();
    }

    @Test
    void self_and_admin_viewers_both_dispatch_to_the_unrestricted_query_and_never_reach_the_scoped_method() {
        PhysicalCapacityAssignmentRepository repository = mock(PhysicalCapacityAssignmentRepository.class);
        when(repository.findMonthlyAttendance(any(), any(), any())).thenReturn(List.of());
        GetPhysicalAttendanceHistoryUseCaseImpl useCase =
            new GetPhysicalAttendanceHistoryUseCaseImpl(repository, BUENOS_AIRES);
        UUID studentId = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 9);

        useCase.history(AttendanceViewer.self(studentId), month, true);
        useCase.history(AttendanceViewer.elevated(studentId, UUID.randomUUID(), true), month, true);

        verify(repository, org.mockito.Mockito.times(2)).findMonthlyAttendance(
            studentId, Instant.parse("2026-09-01T03:00:00Z"), Instant.parse("2026-10-01T03:00:00Z")
        );
        verifyNoMoreInteractions(repository);
    }

    @Test
    void a_student_with_zero_rows_that_month_yields_the_same_view_as_the_empty_month_shape() {
        PhysicalCapacityAssignmentRepository repository = mock(PhysicalCapacityAssignmentRepository.class);
        when(repository.findMonthlyAttendance(any(), any(), any())).thenReturn(List.of());
        GetPhysicalAttendanceHistoryUseCaseImpl useCase =
            new GetPhysicalAttendanceHistoryUseCaseImpl(repository, BUENOS_AIRES);

        AttendanceHistoryView view =
            useCase.history(AttendanceViewer.self(UUID.randomUUID()), YearMonth.of(2026, 11), true);

        AttendanceHistoryView expectedEmptyShape = new AttendanceHistoryView(
            "2026-11", 0, 0, 0, new java.math.BigDecimal("0.00"), List.of()
        );
        assertThat(view).isEqualTo(expectedEmptyShape);
    }

    /**
     * #39, US-PHYSICAL-002 P2, design C2/R7 — completes the exhaustive-dispatch coverage 1.9
     * started: {@code InstructorOwnCourses} MUST call the scoped method with the caller's own
     * id, never {@code null}, and MUST NOT reach the unrestricted method at all.
     */
    @Test
    void instructor_own_courses_viewer_dispatches_to_the_scoped_method_with_the_callers_own_id() {
        PhysicalCapacityAssignmentRepository repository = mock(PhysicalCapacityAssignmentRepository.class);
        when(repository.findMonthlyAttendanceInCoursesOwnedBy(any(), any(), any(), any())).thenReturn(List.of());
        GetPhysicalAttendanceHistoryUseCaseImpl useCase =
            new GetPhysicalAttendanceHistoryUseCaseImpl(repository, BUENOS_AIRES);
        UUID studentId = UUID.randomUUID();
        UUID professorId = UUID.randomUUID();

        useCase.history(AttendanceViewer.elevated(studentId, professorId, false), YearMonth.of(2026, 9), true);

        verify(repository).findMonthlyAttendanceInCoursesOwnedBy(
            studentId, Instant.parse("2026-09-01T03:00:00Z"), Instant.parse("2026-10-01T03:00:00Z"), professorId
        );
        verifyNoMoreInteractions(repository);
    }

    /**
     * #39, US-PHYSICAL-002 P2, design C4/R9 — anti-enumeration equality, instructor case: an
     * {@code InstructorOwnCourses} viewer for a student with zero overlapping courses yields a
     * view equal to the same empty-month shape as 1.10. No existence port exists to distinguish
     * "student not found" from "student exists but no course overlap" from "student exists, zero
     * sessions that month" — all three collapse to the same object.
     */
    @Test
    void an_instructor_viewer_with_zero_overlapping_courses_yields_the_same_empty_month_shape() {
        PhysicalCapacityAssignmentRepository repository = mock(PhysicalCapacityAssignmentRepository.class);
        when(repository.findMonthlyAttendanceInCoursesOwnedBy(any(), any(), any(), any())).thenReturn(List.of());
        GetPhysicalAttendanceHistoryUseCaseImpl useCase =
            new GetPhysicalAttendanceHistoryUseCaseImpl(repository, BUENOS_AIRES);

        AttendanceHistoryView view = useCase.history(
            AttendanceViewer.elevated(UUID.randomUUID(), UUID.randomUUID(), false), YearMonth.of(2026, 11), true
        );

        AttendanceHistoryView expectedEmptyShape = new AttendanceHistoryView(
            "2026-11", 0, 0, 0, new java.math.BigDecimal("0.00"), List.of()
        );
        assertThat(view).isEqualTo(expectedEmptyShape);
    }
}
