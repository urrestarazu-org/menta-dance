package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.port.in.ListManagedPhysicalSessionsUseCase;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.model.CourseId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Lists the sessions of a course for its manager, whatever their status.
 *
 * <p>Ownership is checked through {@code CourseOwnershipGuard}. Omitted bounds default to a very
 * wide window around the current instant, so callers can read the full history without supplying a
 * range.
 */
@RequiredArgsConstructor
public class ListManagedPhysicalSessionsUseCaseImpl implements ListManagedPhysicalSessionsUseCase {

    /**
     * ~10 years each way: comfortably inside MySQL's {@code DATETIME} range
     * (years 1000-9999), generous enough to behave as "unbounded" for any
     * realistic session history or schedule. A true unbounded query isn't an
     * option — {@code findManaged} always needs concrete bounds — so a caller
     * omitting {@code from}/{@code to} gets this wide window instead.
     */
    private static final Duration DEFAULT_RANGE = Duration.ofDays(3650);

    private final PhysicalCourseRepository courseRepository;
    private final PhysicalSessionRepository sessionRepository;
    private final Clock clock;

    @Override
    public List<PhysicalSessionManagementResult> list(
        String courseId, Instant from, Instant to, UUID actingUserId, boolean actingAsAdmin
    ) {
        CourseId parsedCourseId = CourseId.of(courseId);
        CourseOwnershipGuard.resolveOwnedCourse(courseRepository, parsedCourseId, actingUserId, actingAsAdmin);
        Instant now = clock.now();
        Instant effectiveFrom = from != null ? from : now.minus(DEFAULT_RANGE);
        Instant effectiveTo = to != null ? to : now.plus(DEFAULT_RANGE);
        return sessionRepository.findManaged(parsedCourseId, effectiveFrom, effectiveTo).stream()
            .map(PhysicalSessionResultMapper::toResult)
            .toList();
    }
}
