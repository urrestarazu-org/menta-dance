package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.VirtualCourseDetail;

/**
 * Use case that reads a virtual course with its modules and lessons (#367): it prefills the edit form and feeds
 * the module and lesson screens.
 *
 * <p>Part of Clean Architecture application layer. Ownership is the API's: its refusals reach the caller as
 * {@code VirtualCourseManagementApiClient.VirtualCourseManagementException}.
 * </p>
 */
public interface GetManagedVirtualCourseUseCase {

    /**
     * Reads the course.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course as it came in the URL; one that is not a UUID is a course that
     *                    does not exist ({@code COURSE_NOT_FOUND}, without calling the API)
     * @return the course as the API reports it
     */
    VirtualCourseDetail execute(String accessToken, String courseId);
}
