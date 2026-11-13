package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.AttendanceHistoryRow;
import com.menta.physical.application.dto.AttendanceHistoryView;
import com.menta.physical.application.dto.AttendanceViewer;
import com.menta.physical.application.port.in.GetPhysicalAttendanceHistoryUseCase;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.domain.model.InstantRange;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * #39, US-PHYSICAL-002, design C1-C4, #729. Never checks whether the student exists — no
 * {@code UserQueryPort}, no existence probe, no {@code 404} path anywhere (C4): the query has no
 * branch that distinguishes "student does not exist" from "student exists but no overlap" from
 * "student exists but no sessions in that range", so all three converge on the same empty view.
 *
 * <p>Every backend date is UTC: the range arrives as instants computed by the client and goes to the
 * repository untouched, with no time zone. It is capped at {@link #MAX_RANGE} to bound the response.
 */
public class GetPhysicalAttendanceHistoryUseCaseImpl implements GetPhysicalAttendanceHistoryUseCase {

    /** Widest range a single read may span (three calendar months at most); exactly this width is accepted. */
    static final Duration MAX_RANGE = Duration.ofDays(92);

    private final PhysicalCapacityAssignmentRepository assignmentRepository;

    public GetPhysicalAttendanceHistoryUseCaseImpl(PhysicalCapacityAssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    public AttendanceHistoryView history(AttendanceViewer viewer, Instant from, Instant to, boolean includeAbsent) {
        InstantRange range = InstantRange.within(from, to, MAX_RANGE);

        List<AttendanceHistoryRow> rows = switch (viewer) {
            case AttendanceViewer.Self self ->
                assignmentRepository.findAttendance(self.studentId(), range.from(), range.to());
            case AttendanceViewer.Admin admin ->
                assignmentRepository.findAttendance(admin.studentId(), range.from(), range.to());
            case AttendanceViewer.InstructorOwnCourses instructor ->
                assignmentRepository.findAttendanceInCoursesOwnedBy(
                    instructor.studentId(), range.from(), range.to(), instructor.professorId()
                );
        };

        return AttendanceAssembler.assemble(range.from(), range.to(), rows, includeAbsent);
    }
}
