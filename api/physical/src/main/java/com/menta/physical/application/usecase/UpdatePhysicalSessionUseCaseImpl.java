package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.dto.UpdatePhysicalSessionCommand;
import com.menta.physical.application.port.in.UpdatePhysicalSessionUseCase;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.SessionAlreadyOccurredException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public class UpdatePhysicalSessionUseCaseImpl implements UpdatePhysicalSessionUseCase {

    private final PhysicalCourseRepository courseRepository;
    private final PhysicalSessionRepository sessionRepository;

    public UpdatePhysicalSessionUseCaseImpl(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public PhysicalSessionManagementResult update(
        String sessionId, UpdatePhysicalSessionCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        PhysicalSession session = sessionRepository.findById(SessionId.of(sessionId))
            .orElseThrow(SessionNotFoundException::new);
        CourseOwnershipGuard.resolveOwnedCourse(
            courseRepository, CourseId.of(session.getCourseId().toString()), actingUserId, actingAsAdmin
        );

        Instant now = Instant.now();
        if (session.hasOccurred(now)) {
            throw new SessionAlreadyOccurredException();
        }

        PhysicalSession updated = applyPatch(session, command);
        return PhysicalSessionResultMapper.toResult(sessionRepository.save(updated));
    }

    private static PhysicalSession applyPatch(PhysicalSession session, UpdatePhysicalSessionCommand command) {
        PhysicalSession result = session;
        if (command.date().isPresent() || command.startTime().isPresent()) {
            Instant currentScheduledAt = result.getScheduledAt();
            java.time.LocalDate date = command.date().orElseGet(
                () -> currentScheduledAt.atZone(ZoneOffset.UTC).toLocalDate()
            );
            java.time.LocalTime time = command.startTime().orElseGet(
                () -> currentScheduledAt.atZone(ZoneOffset.UTC).toLocalTime()
            );
            result = result.withSchedule(LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC));
        }
        if (command.capacity().isPresent()) {
            result = result.withCapacity(command.capacity().get());
        }
        if (command.notes().isPresent()) {
            result = result.withNotes(command.notes().get());
        }
        if (command.status().isPresent() && command.status().get() == SessionStatus.CANCELLED) {
            result = result.cancel();
        }
        return result;
    }
}
