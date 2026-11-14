package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.SessionChanges;
import com.menta.bff.application.dto.UpdateSessionCommand;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link UpdateCourseSessionUseCase}: converts the instant, the API decides the rest. */
@RequiredArgsConstructor
public class UpdateCourseSessionUseCaseImpl implements UpdateCourseSessionUseCase {

    private final PhysicalCourseManagementApiClient courseApiClient;
    private final ZoneId displayZone;

    @Override
    public ManagedSessionView execute(String accessToken, String sessionId, UpdateSessionCommand command) {
        String token = CourseManagementSupport.token(accessToken);
        String id = CourseManagementSupport.sessionId(sessionId);
        LocalDateTime utc = command.localDate() == null
                ? null : ClassLabels.toUtc(command.localDate(), command.localTime(), displayZone);
        SessionChanges changes = new SessionChanges(utc == null ? null : utc.toLocalDate(),
                utc == null ? null : utc.toLocalTime(), command.capacity(), command.notes(), command.status());
        return CourseManagementSupport.view(courseApiClient.updateSession(token, id, changes), displayZone);
    }
}
