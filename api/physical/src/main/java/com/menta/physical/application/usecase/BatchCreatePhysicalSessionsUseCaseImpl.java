package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.BatchCreatePhysicalSessionsCommand;
import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.port.in.BatchCreatePhysicalSessionsUseCase;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.EmptySessionBatchException;
import com.menta.physical.domain.exception.SessionAlreadyExistsException;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalSession;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates the sessions of a course over a date range after checking that the caller manages it.
 *
 * <p>Uses {@code CourseOwnershipGuard} for authorization and persists the whole batch through the
 * session repository's batch write. The batch is all-or-nothing: a range with no date on the
 * course's day of week fails with {@code EmptySessionBatchException}, and if the course already has
 * a session (of any status) at any generated instant it fails with {@code
 * SessionAlreadyExistsException} and creates none (#743).
 */
public class BatchCreatePhysicalSessionsUseCaseImpl implements BatchCreatePhysicalSessionsUseCase {

    private final PhysicalCourseRepository courseRepository;
    private final PhysicalSessionRepository sessionRepository;

    public BatchCreatePhysicalSessionsUseCaseImpl(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public List<PhysicalSessionManagementResult> createBatch(
        String courseId, BatchCreatePhysicalSessionsCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        PhysicalCourse course = CourseOwnershipGuard.resolveOwnedCourse(
            courseRepository, CourseId.of(courseId), actingUserId, actingAsAdmin
        );

        List<Instant> instants = new ArrayList<>();
        for (LocalDate date = command.fromDate(); !date.isAfter(command.toDate()); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == course.getDayOfWeek()) {
                instants.add(LocalDateTime.of(date, course.getStartTime()).toInstant(ZoneOffset.UTC));
            }
        }
        if (instants.isEmpty()) {
            throw new EmptySessionBatchException();
        }

        List<Instant> existing = sessionRepository.findExistingScheduledAt(course.getId(), instants);
        if (!existing.isEmpty()) {
            throw new SessionAlreadyExistsException(existing);
        }

        List<PhysicalSession> generated = instants.stream()
            .map(instant -> PhysicalSession.create(course.getId(), instant, course.getCapacity(), null))
            .toList();
        return sessionRepository.saveAll(generated).stream().map(PhysicalSessionResultMapper::toResult).toList();
    }
}
