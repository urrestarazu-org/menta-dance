package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.BatchCreatePhysicalSessionsCommand;
import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.port.in.BatchCreatePhysicalSessionsUseCase;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

        List<PhysicalSession> generated = new ArrayList<>();
        for (LocalDate date = command.fromDate(); !date.isAfter(command.toDate()); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == course.getDayOfWeek()) {
                generated.add(PhysicalSession.create(
                    course.getId(),
                    LocalDateTime.of(date, course.getStartTime()).toInstant(ZoneOffset.UTC),
                    course.getCapacity(),
                    null
                ));
            }
        }

        return sessionRepository.saveAll(generated).stream().map(PhysicalSessionResultMapper::toResult).toList();
    }
}
