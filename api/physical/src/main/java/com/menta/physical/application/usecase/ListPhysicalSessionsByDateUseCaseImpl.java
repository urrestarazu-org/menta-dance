package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalDaySessionView;
import com.menta.physical.application.dto.SessionWithCourse;
import com.menta.physical.application.port.in.ListPhysicalSessionsByDateUseCase;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.model.PhysicalSession;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * #355. "The day" is the academy's calendar day, the same zone {@link ListMyPhysicalSessionsUseCaseImpl}
 * and {@link GetPhysicalAttendanceHistoryUseCaseImpl} use, so a session at 22:00 local is never pushed to
 * the next day by UTC. The range is half-open {@code [start of the day, start of the next day)} and is
 * computed with {@code atStartOfDay}, so a daylight-saving day is not assumed to last 24 hours. The
 * repository resolves the whole listing, counts and course data included, in one read.
 */
public class ListPhysicalSessionsByDateUseCaseImpl implements ListPhysicalSessionsByDateUseCase {

    private final PhysicalSessionRepository sessionRepository;
    private final ZoneId zoneId;

    public ListPhysicalSessionsByDateUseCaseImpl(PhysicalSessionRepository sessionRepository, ZoneId zoneId) {
        this.sessionRepository = sessionRepository;
        this.zoneId = zoneId;
    }

    @Override
    public List<PhysicalDaySessionView> list(LocalDate date) {
        Instant from = date.atStartOfDay(zoneId).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zoneId).toInstant();
        return sessionRepository.findManagedAcrossCourses(from, to).stream()
            .map(ListPhysicalSessionsByDateUseCaseImpl::toView)
            .toList();
    }

    private static PhysicalDaySessionView toView(SessionWithCourse row) {
        PhysicalSession session = row.session();
        return new PhysicalDaySessionView(
            session.getId().getValue(),
            session.getCourseId().getValue(),
            row.courseTitle(),
            session.getScheduledAt(),
            session.getScheduledAt().plus(Duration.ofMinutes(row.courseDurationMinutes())),
            session.getStatus(),
            session.getCapacity(),
            session.getOccupiedSpots()
        );
    }
}
