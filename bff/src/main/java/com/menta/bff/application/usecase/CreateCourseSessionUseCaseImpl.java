package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CreateSessionCommand;
import com.menta.bff.application.dto.SessionCreation;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link CreateCourseSessionUseCase}: converts the instant, the API decides the rest. */
@RequiredArgsConstructor
public class CreateCourseSessionUseCaseImpl implements CreateCourseSessionUseCase {

    private final PhysicalCourseManagementApiClient courseApiClient;
    private final ZoneId displayZone;

    @Override
    public ManagedSessionView execute(String accessToken, String courseId, CreateSessionCommand command) {
        String token = CourseManagementSupport.token(accessToken);
        String id = CourseManagementSupport.courseId(courseId);
        LocalDateTime utc = ClassLabels.toUtc(command.localDate(), command.localTime(), displayZone);
        SessionCreation creation = new SessionCreation(
                utc.toLocalDate(), utc.toLocalTime(), command.capacity(), command.notes());
        return CourseManagementSupport.view(courseApiClient.createSession(token, id, creation), displayZone);
    }
}
