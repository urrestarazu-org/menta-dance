package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualCourse;

/**
 * Use case that takes a virtual course back to draft (#367).
 *
 * <p>Part of Clean Architecture application layer. Ownership is the API's: its refusals reach the caller as
 * {@code VirtualCourseManagementApiClient.VirtualCourseManagementException}.
 * </p>
 */
public interface UnpublishVirtualCourseUseCase {

    /**
     * Unpublishes the course.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course; one that is not a UUID is a course that does not exist
     * @return the course after the change
     */
    ManagedVirtualCourse execute(String accessToken, String courseId);
}
