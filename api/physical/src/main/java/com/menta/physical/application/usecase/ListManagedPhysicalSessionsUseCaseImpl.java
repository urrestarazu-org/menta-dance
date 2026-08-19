package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.port.in.ListManagedPhysicalSessionsUseCase;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.model.CourseId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    public ListManagedPhysicalSessionsUseCaseImpl(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public List<PhysicalSessionManagementResult> list(
        String courseId, Instant from, Instant to, UUID actingUserId, boolean actingAsAdmin
    ) {
        CourseId parsedCourseId = CourseId.of(courseId);
        CourseOwnershipGuard.resolveOwnedCourse(courseRepository, parsedCourseId, actingUserId, actingAsAdmin);
        Instant now = Instant.now();
        Instant effectiveFrom = from != null ? from : now.minus(DEFAULT_RANGE);
        Instant effectiveTo = to != null ? to : now.plus(DEFAULT_RANGE);
        return sessionRepository.findManaged(parsedCourseId, effectiveFrom, effectiveTo).stream()
            .map(PhysicalSessionResultMapper::toResult)
            .toList();
    }
}
