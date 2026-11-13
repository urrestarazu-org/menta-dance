package com.menta.physical.application.dto;

import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.SessionStatus;
import com.menta.shared.physical.AssignmentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The roster of one session for staff (#355): a minimal header plus the students with a confirmed assignment.
 * Holds and pending payments are never part of it.
 */
public record PhysicalSessionRosterView(SessionHeader session, List<Student> students) {

    /**
     * Minimal session data staff need next to the roster.
     *
     * @param studentCount number of students in the roster, i.e. confirmed assignments of the session
     */
    public record SessionHeader(
        UUID sessionId, UUID courseId, String courseName, Instant scheduledAt, SessionStatus status, int capacity,
        int studentCount
    ) {
    }

    /**
     * One student of the roster. {@code checkedInAt} and {@code checkInKind} are {@code null} while the
     * attendance is {@link RosterAttendanceStatus#PENDING}.
     */
    public record Student(
        UUID studentId, String displayName, AssignmentType assignmentType, RosterAttendanceStatus attendanceStatus,
        Instant checkedInAt, AttendanceKind checkInKind
    ) {
    }
}
