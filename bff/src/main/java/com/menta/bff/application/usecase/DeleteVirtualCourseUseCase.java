package com.menta.bff.application.usecase;

/**
 * Use case that deletes a draft virtual course (#367).
 *
 * <p>Part of Clean Architecture application layer. That only a draft can be deleted, and that a course with
 * student progress cannot (#834), is the API's: it refuses with {@code
 * VirtualCourseManagementApiClient.VirtualCourseManagementException} ({@code COURSE_NOT_DRAFT} or {@code
 * COURSE_HAS_PROGRESS}).
 * </p>
 */
public interface DeleteVirtualCourseUseCase {

    /**
     * Deletes the course.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course; one that is not a UUID is a course that does not exist
     */
    void execute(String accessToken, String courseId);
}
