package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.AssignedSessionRow;
import com.menta.physical.application.dto.AssignedSessionView;
import com.menta.physical.application.dto.AssignedSessionsView;
import com.menta.physical.application.port.in.ListMyPhysicalSessionsUseCase;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.domain.model.CheckInWindow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * #356. The range starts at the beginning of today in the academy's zone (so a class earlier
 * today still shows) and spans {@code windowDays} calendar days; the zone keeps "today" aligned
 * with how the academy and {@link GetPhysicalAttendanceHistoryUseCaseImpl} read the calendar.
 */
public class ListMyPhysicalSessionsUseCaseImpl implements ListMyPhysicalSessionsUseCase {

    private final PhysicalCapacityAssignmentRepository assignmentRepository;
    private final Clock clock;
    private final ZoneId zoneId;
    private final int windowDays;
    private final CheckInWindow checkInWindow;

    public ListMyPhysicalSessionsUseCaseImpl(
        PhysicalCapacityAssignmentRepository assignmentRepository, Clock clock, ZoneId zoneId,
        int windowDays, CheckInWindow checkInWindow
    ) {
        if (windowDays < 1) {
            throw new IllegalArgumentException("windowDays must be at least 1 but was " + windowDays);
        }
        this.assignmentRepository = assignmentRepository;
        this.clock = clock;
        this.zoneId = zoneId;
        this.windowDays = windowDays;
        this.checkInWindow = checkInWindow;
    }

    @Override
    public AssignedSessionsView list(UUID studentId) {
        Instant now = clock.now();
        LocalDate today = now.atZone(zoneId).toLocalDate();
        Instant from = today.atStartOfDay(zoneId).toInstant();
        Instant to = today.plusDays(windowDays).atStartOfDay(zoneId).toInstant();

        List<AssignedSessionView> sessions = assignmentRepository.findAssignedSessions(studentId, from, to)
            .stream()
            .map(row -> toView(row, now))
            .toList();
        return new AssignedSessionsView(from, to, sessions);
    }

    private AssignedSessionView toView(AssignedSessionRow row, Instant now) {
        return new AssignedSessionView(
            row.sessionId(), row.courseId(), row.courseName(), row.instructorName(), row.scheduledAt(),
            checkInWindow.isOpenAt(row.scheduledAt(), now)
        );
    }
}
