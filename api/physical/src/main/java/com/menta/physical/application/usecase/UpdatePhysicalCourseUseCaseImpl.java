package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import com.menta.physical.application.dto.UpdatePhysicalCourseCommand;
import com.menta.physical.application.port.in.UpdatePhysicalCourseUseCase;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.CourseHasActiveAssignmentsException;
import com.menta.physical.domain.exception.CourseNotFoundException;
import com.menta.physical.domain.exception.CourseNotOwnedException;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import java.util.UUID;

public class UpdatePhysicalCourseUseCaseImpl implements UpdatePhysicalCourseUseCase {

    private final PhysicalCourseRepository courseRepository;
    private final PhysicalSessionRepository sessionRepository;

    public UpdatePhysicalCourseUseCaseImpl(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        this.courseRepository = courseRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public PhysicalCourseManagementResult update(
        String courseId, UpdatePhysicalCourseCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        PhysicalCourse course = courseRepository.findById(CourseId.of(courseId))
            .orElseThrow(CourseNotFoundException::new);
        if (!actingAsAdmin && !course.isOwnedBy(actingUserId)) {
            throw new CourseNotOwnedException();
        }

        PhysicalCourse updated = applyPatch(course, command);

        if (deactivates(command) && sessionRepository.hasFutureAssignedSessions(course.getId())) {
            throw new CourseHasActiveAssignmentsException();
        }

        return PhysicalCourseResultMapper.toResult(courseRepository.save(updated));
    }

    private static boolean deactivates(UpdatePhysicalCourseCommand command) {
        return command.status().filter(status -> status == CourseStatus.INACTIVE).isPresent();
    }

    private static PhysicalCourse applyPatch(PhysicalCourse course, UpdatePhysicalCourseCommand command) {
        PhysicalCourse result = course;
        if (command.title().isPresent()) {
            result = result.withTitle(command.title().get());
        }
        if (command.description().isPresent()) {
            result = result.withDescription(command.description().get());
        }
        if (command.dayOfWeek().isPresent() || command.startTime().isPresent()
            || command.durationMinutes().isPresent()) {
            result = result.withSchedule(
                command.dayOfWeek().orElse(result.getDayOfWeek()),
                command.startTime().orElse(result.getStartTime()),
                command.durationMinutes().orElse(result.getDurationMinutes())
            );
        }
        if (command.level().isPresent()) {
            result = result.withLevel(command.level().get());
        }
        if (command.capacity().isPresent()) {
            result = result.withCapacity(command.capacity().get());
        }
        if (command.status().isPresent()) {
            result = result.withStatus(command.status().get());
        }
        return result;
    }
}
