package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CourseCreation;
import com.menta.bff.application.dto.CreateCourseCommand;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.usecase.ClassLabels.WeeklySlot;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link CreateManagedCourseUseCase}. The only thing decided here is that the instructor is
 * never left to the API's default: a missing one is the same {@code PROFESSOR_REQUIRED} the API would answer an
 * administrator.
 */
@RequiredArgsConstructor
public class CreateManagedCourseUseCaseImpl implements CreateManagedCourseUseCase {

    private final PhysicalCourseManagementApiClient courseApiClient;
    private final ZoneId displayZone;

    @Override
    public ManagedCourseView execute(String accessToken, CreateCourseCommand command) {
        String token = CourseManagementSupport.token(accessToken);
        String professorId = UuidFormat.normalize(command.professorId());
        if (professorId == null) {
            throw new CourseManagementException(Code.PROFESSOR_REQUIRED);
        }
        WeeklySlot utc = ClassLabels.toUtcSlot(command.localDay(), command.localTime(), displayZone);
        CourseCreation creation = new CourseCreation(command.title(), command.description(), professorId,
                command.professorName(), utc.day(), utc.time(), command.durationMinutes(), command.level(),
                command.capacity());
        return CourseManagementSupport.view(courseApiClient.createCourse(token, creation), displayZone);
    }
}
