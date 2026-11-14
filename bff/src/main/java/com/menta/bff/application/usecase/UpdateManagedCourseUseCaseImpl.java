package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CourseChanges;
import com.menta.bff.application.dto.UpdateCourseCommand;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import com.menta.bff.application.usecase.ClassLabels.WeeklySlot;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link UpdateManagedCourseUseCase}: converts the schedule, the API decides the rest. */
@RequiredArgsConstructor
public class UpdateManagedCourseUseCaseImpl implements UpdateManagedCourseUseCase {

    private final PhysicalCourseManagementApiClient courseApiClient;
    private final ZoneId displayZone;

    @Override
    public ManagedCourseView execute(String accessToken, String courseId, UpdateCourseCommand command) {
        String token = CourseManagementSupport.token(accessToken);
        String id = CourseManagementSupport.courseId(courseId);
        WeeklySlot utc = command.localDay() == null
                ? null : ClassLabels.toUtcSlot(command.localDay(), command.localTime(), displayZone);
        CourseChanges changes = new CourseChanges(command.title(), command.description(),
                utc == null ? null : utc.day(), utc == null ? null : utc.time(), command.durationMinutes(),
                command.level(), command.capacity(), command.status());
        return CourseManagementSupport.view(courseApiClient.updateCourse(token, id, changes), displayZone);
    }
}
