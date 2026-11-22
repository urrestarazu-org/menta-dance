package com.menta.bff.application.usecase;

/**
 * Use case that deletes a lesson from the structure of a course (#831).
 *
 * <p>Part of Clean Architecture application layer. That the lesson also takes the students' progress with it and
 * that the lessons left are renumbered is the API's; so are ownership and existence, which reach the caller as
 * {@code VirtualCourseManagementApiClient.VirtualCourseManagementException}.
 * </p>
 */
public interface DeleteVirtualLessonUseCase {

    /**
     * Deletes the lesson.
     *
     * @param accessToken caller's access token
     * @param lessonId    identifier of the lesson; one that is not a UUID is a lesson that does not exist
     */
    void execute(String accessToken, String lessonId);
}
