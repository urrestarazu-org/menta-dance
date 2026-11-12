package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.PhysicalSessionRosterView;
import com.menta.physical.domain.model.SessionStatus;
import com.menta.shared.physical.AssignmentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** #355 — wire shape of {@code GET /api/v1/admin/physical/sessions/{sessionId}/roster}; every instant is UTC. */
public record PhysicalSessionRosterResponse(Session session, List<Student> students) {

    /** Minimal session header. */
    public record Session(
        UUID sessionId, UUID courseId, String courseName, Instant scheduledAt, SessionStatus status, int capacity,
        int studentCount
    ) {
    }

    /**
     * One student with a confirmed assignment. {@code checkedInAt} and {@code checkInKind} stay explicit
     * {@code null}s while {@code attendanceStatus} is {@code PENDING}.
     */
    public record Student(
        UUID studentId, String displayName, AssignmentType assignmentType, String attendanceStatus,
        Instant checkedInAt, String checkInKind
    ) {

        static Student from(PhysicalSessionRosterView.Student view) {
            return new Student(
                view.studentId(), view.displayName(), view.assignmentType(), view.attendanceStatus().name(),
                view.checkedInAt(), view.checkInKind() == null ? null : view.checkInKind().name()
            );
        }
    }

    /** Maps the application view to the wire shape, keeping every value and the order as is. */
    public static PhysicalSessionRosterResponse from(PhysicalSessionRosterView view) {
        PhysicalSessionRosterView.SessionHeader header = view.session();
        return new PhysicalSessionRosterResponse(
            new Session(
                header.sessionId(), header.courseId(), header.courseName(), header.scheduledAt(), header.status(),
                header.capacity(), header.studentCount()
            ),
            view.students().stream().map(Student::from).toList()
        );
    }
}
