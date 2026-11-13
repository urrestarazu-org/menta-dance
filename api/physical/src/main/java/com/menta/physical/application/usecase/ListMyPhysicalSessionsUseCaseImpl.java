package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.AssignedSessionRow;
import com.menta.physical.application.dto.AssignedSessionView;
import com.menta.physical.application.dto.AssignedSessionsView;
import com.menta.physical.application.port.in.ListMyPhysicalSessionsUseCase;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.domain.model.CheckInWindow;
import com.menta.physical.domain.model.InstantRange;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * #356, #729. Every backend date is UTC, so the range arrives as instants computed by the client and
 * goes to the repository untouched: no time zone, no day arithmetic. The range is capped at
 * {@link #MAX_RANGE} to bound the response. The {@link Clock} is only used for {@code checkInOpen},
 * never to decide the range.
 */
public class ListMyPhysicalSessionsUseCaseImpl implements ListMyPhysicalSessionsUseCase {

    /** Widest range a single read may span (three calendar months at most); exactly this width is accepted. */
    static final Duration MAX_RANGE = Duration.ofDays(92);

    private final PhysicalCapacityAssignmentRepository assignmentRepository;
    private final Clock clock;
    private final CheckInWindow checkInWindow;

    public ListMyPhysicalSessionsUseCaseImpl(
        PhysicalCapacityAssignmentRepository assignmentRepository, Clock clock, CheckInWindow checkInWindow
    ) {
        this.assignmentRepository = assignmentRepository;
        this.clock = clock;
        this.checkInWindow = checkInWindow;
    }

    @Override
    public AssignedSessionsView list(UUID studentId, Instant from, Instant to) {
        InstantRange range = InstantRange.within(from, to, MAX_RANGE);
        Instant now = clock.now();

        List<AssignedSessionView> sessions = assignmentRepository
            .findAssignedSessions(studentId, range.from(), range.to())
            .stream()
            .map(row -> toView(row, now))
            .toList();
        return new AssignedSessionsView(range.from(), range.to(), sessions);
    }

    private AssignedSessionView toView(AssignedSessionRow row, Instant now) {
        return new AssignedSessionView(
            row.sessionId(), row.courseId(), row.courseName(), row.instructorName(), row.scheduledAt(),
            checkInWindow.isOpenAt(row.scheduledAt(), now), row.assignmentType()
        );
    }
}
