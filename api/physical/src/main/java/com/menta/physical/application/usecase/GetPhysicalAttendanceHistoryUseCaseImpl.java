package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.AttendanceHistoryRow;
import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceViewer;
import com.menta.physical.application.port.in.GetPhysicalAttendanceHistoryUseCase;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * #39, US-PHYSICAL-002, design C1-C4. Never checks whether the student exists — no
 * {@code UserQueryPort}, no existence probe, no {@code 404} path anywhere (C4): the query has no
 * branch that distinguishes "student does not exist" from "student exists but no overlap" from
 * "student exists but no sessions that month", so all three converge on the same empty view.
 */
public class GetPhysicalAttendanceHistoryUseCaseImpl implements GetPhysicalAttendanceHistoryUseCase {

    private final PhysicalCapacityAssignmentRepository assignmentRepository;
    private final ZoneId zoneId;

    public GetPhysicalAttendanceHistoryUseCaseImpl(
        PhysicalCapacityAssignmentRepository assignmentRepository, ZoneId zoneId
    ) {
        this.assignmentRepository = assignmentRepository;
        this.zoneId = zoneId;
    }

    @Override
    public AttendanceHistoryView history(AttendanceViewer viewer, YearMonth month, boolean includeAbsent) {
        Instant monthStart = month.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant monthNext = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant();

        List<AttendanceHistoryRow> rows = switch (viewer) {
            case AttendanceViewer.Self self ->
                assignmentRepository.findMonthlyAttendance(self.studentId(), monthStart, monthNext);
            case AttendanceViewer.Admin admin ->
                assignmentRepository.findMonthlyAttendance(admin.studentId(), monthStart, monthNext);
            case AttendanceViewer.InstructorOwnCourses instructor ->
                assignmentRepository.findMonthlyAttendanceInCoursesOwnedBy(
                    instructor.studentId(), monthStart, monthNext, instructor.professorId()
                );
        };

        return MonthlyAttendanceAssembler.assemble(month.toString(), rows, includeAbsent);
    }
}
