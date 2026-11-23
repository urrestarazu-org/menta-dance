package com.menta.bff.application.usecase;

/**
 * Use case that deletes a draft virtual course (#367).
 *
 * <p>Part of Clean Architecture application layer. That only a draft can be deleted, that a course with
 * student progress cannot (#834), and that a course a plan or subscription of Billing references cannot
 * (#856), is the API's: it refuses with {@code
 * VirtualCourseManagementApiClient.VirtualCourseManagementException} ({@code COURSE_NOT_DRAFT}, {@code
 * COURSE_HAS_PROGRESS} or {@code COURSE_IN_USE}).
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
