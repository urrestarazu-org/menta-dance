package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link ListManagedCoursesUseCase}: the API decides which courses the caller sees. */
@RequiredArgsConstructor
public class ListManagedCoursesUseCaseImpl implements ListManagedCoursesUseCase {

    private final PhysicalCourseManagementApiClient courseApiClient;
    private final ZoneId displayZone;

    @Override
    public List<ManagedCourseView> execute(String accessToken) {
        return courseApiClient.listCourses(CourseManagementSupport.token(accessToken)).stream()
                .map(course -> CourseManagementSupport.view(course, displayZone))
                .toList();
    }
}
