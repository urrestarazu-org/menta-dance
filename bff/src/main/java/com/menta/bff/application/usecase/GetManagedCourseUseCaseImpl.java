package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link GetManagedCourseUseCase}. */
@RequiredArgsConstructor
public class GetManagedCourseUseCaseImpl implements GetManagedCourseUseCase {

    private final PhysicalCourseManagementApiClient courseApiClient;
    private final ZoneId displayZone;

    @Override
    public ManagedCourseView execute(String accessToken, String courseId) {
        String id = CourseManagementSupport.courseId(courseId);
        return courseApiClient.listCourses(CourseManagementSupport.token(accessToken)).stream()
                .filter(course -> id.equalsIgnoreCase(course.courseId()))
                .findFirst()
                .map(course -> CourseManagementSupport.view(course, displayZone))
                .orElseThrow(() -> new CourseManagementException(Code.COURSE_NOT_FOUND));
    }
}
