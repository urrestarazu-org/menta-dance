package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.VirtualLessonLocation;

/**
 * Use case that finds one lesson of a course (#831). The API has no read of a single lesson, so it reads the course
 * detail and looks the module and then the lesson up by id.
 *
 * <p>Part of Clean Architecture application layer. A lesson that is not in that module is
 * {@code LESSON_NOT_FOUND} and a module that is not in the course {@code MODULE_NOT_FOUND}, as the API words them;
 * the API's own refusals reach the caller as
 * {@code VirtualCourseManagementApiClient.VirtualCourseManagementException}.
 * </p>
 */
public interface GetManagedVirtualLessonUseCase {

    /**
     * Finds the lesson.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course; one that is not a UUID is a course that does not exist
     * @param moduleId    identifier of the module; one that is not a UUID is a module that does not exist
     * @param lessonId    identifier of the lesson; one that is not a UUID is a lesson that does not exist. Any of
     *                    the three is checked before the API is called
     * @return the lesson with its module
     */
    VirtualLessonLocation execute(String accessToken, String courseId, String moduleId, String lessonId);
}
