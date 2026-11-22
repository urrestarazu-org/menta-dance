package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualCourse;

/**
 * Use case that publishes a virtual course (#367).
 *
 * <p>Part of Clean Architecture application layer. Whether the course is ready is the API's: it refuses with
 * {@code VirtualCourseManagementApiClient.VirtualCourseManagementException} ({@code COURSE_NOT_PUBLISHABLE}, with
 * the reason in Spanish).
 * </p>
 */
public interface PublishVirtualCourseUseCase {

    /**
     * Publishes the course.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course; one that is not a UUID is a course that does not exist
     * @return the course after the change
     */
    ManagedVirtualCourse execute(String accessToken, String courseId);
}
