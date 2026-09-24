package com.menta.physical.application.port.out;

import com.menta.physical.application.dto.AttendanceHistoryRow;
import com.menta.physical.domain.model.SessionId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only port over {@code physical_capacity_assignments} for the
 * check-in flow (US-PHYSICAL-001 escenarios 1, 3, 7). A row in that table
 * <strong>is</strong> a confirmed assignment — there is no status column to
 * filter on (see {@code V7__physical_courses.sql}: cancellation deletes the
 * row instead of flagging it), so {@code existsConfirmedAssignment} is a
 * plain existence check, not a status comparison.
 *
 * <p>No write method here: this issue never creates or removes assignments,
 * that belongs to the future checkout/capacity flow (#41). Reusing the
 * plural {@code PhysicalCapacityAssignmentRepository} name for a read-only
 * port keeps it symmetric with {@link AttendanceRepository}'s naming even
 * though the write side is intentionally absent for now.</p>
 */
public interface PhysicalCapacityAssignmentRepository {

    boolean existsConfirmedAssignment(SessionId sessionId, UUID studentId);

    /**
     * Unrestricted monthly attendance (#39, US-PHYSICAL-002, design C1): self and {@code ADMIN}
     * readers. One row per assignment whose session's {@code scheduled_at} falls in the
     * half-open {@code [monthStart, monthNext)} range, {@code LEFT JOIN}ed with
     * {@code physical_attendances} for the live {@code ATTENDED}/{@code ABSENT} derivation.
     */
    List<AttendanceHistoryRow> findMonthlyAttendance(UUID studentId, Instant monthStart, Instant monthNext);

    /**
     * Narrowed to sessions of courses whose {@code physical_courses.professor_id == professorId}
     * (#39, design C1) — the filter is applied at the query layer, never post-filtered.
     */
    List<AttendanceHistoryRow> findMonthlyAttendanceInCoursesOwnedBy(
        UUID studentId, Instant monthStart, Instant monthNext, UUID professorId
    );
}
