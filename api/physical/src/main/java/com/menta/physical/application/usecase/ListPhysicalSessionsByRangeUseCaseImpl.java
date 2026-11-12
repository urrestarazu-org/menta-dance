package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalRangeSessionView;
import com.menta.physical.application.dto.SessionWithCourse;
import com.menta.physical.application.port.in.ListPhysicalSessionsByRangeUseCase;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.InvalidSessionRangeException;
import com.menta.physical.domain.model.PhysicalSession;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * #355. Every backend date is UTC, so the range arrives as instants and goes to the repository
 * untouched: no time zone, no day arithmetic. The client turns the local day of its user into
 * {@code [from, to)}. The listing has no pagination, so the range is capped at {@link #MAX_RANGE} to
 * bound the response; the repository resolves the whole listing, counts and course data included,
 * in one read.
 */
public class ListPhysicalSessionsByRangeUseCaseImpl implements ListPhysicalSessionsByRangeUseCase {

    /** Widest range a single listing may span; exactly this width is accepted. */
    static final Duration MAX_RANGE = Duration.ofDays(7);

    private final PhysicalSessionRepository sessionRepository;

    public ListPhysicalSessionsByRangeUseCaseImpl(PhysicalSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public List<PhysicalRangeSessionView> list(Instant from, Instant to) {
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new InvalidSessionRangeException();
        }
        return sessionRepository.findManagedAcrossCourses(from, to).stream()
            .map(ListPhysicalSessionsByRangeUseCaseImpl::toView)
            .toList();
    }

    private static PhysicalRangeSessionView toView(SessionWithCourse row) {
        PhysicalSession session = row.session();
        return new PhysicalRangeSessionView(
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
