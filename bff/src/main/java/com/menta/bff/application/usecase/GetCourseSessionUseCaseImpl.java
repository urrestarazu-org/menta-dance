package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link GetCourseSessionUseCase}. */
@RequiredArgsConstructor
public class GetCourseSessionUseCaseImpl implements GetCourseSessionUseCase {

    private final PhysicalCourseManagementApiClient courseApiClient;
    private final ZoneId displayZone;

    @Override
    public ManagedSessionView execute(String accessToken, String courseId, String sessionId) {
        String token = CourseManagementSupport.token(accessToken);
        String course = CourseManagementSupport.courseId(courseId);
        String session = CourseManagementSupport.sessionId(sessionId);
        return courseApiClient.listCourseSessions(token, course, null, null).stream()
                .filter(found -> session.equalsIgnoreCase(found.sessionId()))
                .findFirst()
                .map(found -> CourseManagementSupport.view(found, displayZone))
                .orElseThrow(() -> new CourseManagementException(Code.SESSION_NOT_FOUND));
    }
}
