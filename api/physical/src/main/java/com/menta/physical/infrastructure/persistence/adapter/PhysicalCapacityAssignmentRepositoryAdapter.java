package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.application.dto.AttendanceHistoryRow;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.repository.MonthlyAttendanceRowProjection;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link PhysicalCapacityAssignmentRepository}. */
@Component
public class PhysicalCapacityAssignmentRepositoryAdapter
    implements PhysicalCapacityAssignmentRepository {

    private final PhysicalCapacityAssignmentJpaRepository assignmentRepository;

    public PhysicalCapacityAssignmentRepositoryAdapter(
        PhysicalCapacityAssignmentJpaRepository assignmentRepository
    ) {
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public boolean existsConfirmedAssignment(SessionId sessionId, UUID studentId) {
        return assignmentRepository.existsBySessionIdAndStudentId(sessionId.getValue(), studentId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<AttendanceHistoryRow> findMonthlyAttendance(UUID studentId, Instant monthStart, Instant monthNext) {
        return toRows(assignmentRepository.findMonthlyAttendance(studentId, monthStart, monthNext, null));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<AttendanceHistoryRow> findMonthlyAttendanceInCoursesOwnedBy(
        UUID studentId, Instant monthStart, Instant monthNext, UUID professorId
    ) {
        return toRows(
            assignmentRepository.findMonthlyAttendance(studentId, monthStart, monthNext, professorId)
        );
    }

    private static List<AttendanceHistoryRow> toRows(List<MonthlyAttendanceRowProjection> projections) {
        return projections.stream()
            .map(row -> new AttendanceHistoryRow(
                row.getSessionId(), row.getScheduledAt(), row.getCourseName(), row.getInstructorName(),
                row.getRecordedAt()
            ))
            .toList();
    }
}
