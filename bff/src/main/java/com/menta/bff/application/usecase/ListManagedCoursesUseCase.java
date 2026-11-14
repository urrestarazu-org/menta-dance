package com.menta.bff.application.usecase;

import java.util.List;

/**
 * Use case that lists the physical courses the caller manages, for the management screens (#368): all of them for
 * an administrator, the own ones for an instructor.
 *
 * <p>Part of Clean Architecture application layer. Failures of the API reach the caller as
 * {@code PhysicalCourseManagementApiClient.CourseManagementException} or
 * {@code PhysicalCourseManagementApiClient.ServiceUnavailableException}, so the screen words each one.
 * </p>
 */
public interface ListManagedCoursesUseCase {

    /**
     * Lists the courses.
     *
     * @param accessToken caller's access token
     * @return the courses, worded in the academy's zone, possibly empty
     */
    List<ManagedCourseView> execute(String accessToken);
}
