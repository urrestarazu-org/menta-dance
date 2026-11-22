package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualLesson;
import com.menta.bff.application.dto.VirtualLessonChanges;

/**
 * Use case that changes a lesson from the edit form (#367). A {@code null} field of the changes stays as it is,
 * and a video assigned to a lesson cannot be cleared.
 *
 * <p>Part of Clean Architecture application layer. Only the token, the lesson id and the presence of the changes
 * are checked here. The length limits (title 255, description 65535 bytes, video id 128) and the per-field
 * messages belong to the edit form, and the business and ownership rules to the API: its refusals reach the
 * caller as {@code VirtualCourseManagementApiClient.VirtualCourseManagementException}. That is not an oversight.
 * </p>
 */
public interface UpdateVirtualLessonUseCase {

    /**
     * Changes the lesson.
     *
     * @param accessToken caller's access token
     * @param lessonId    identifier of the lesson; one that is not a UUID is a lesson that does not exist
     * @param changes     the fields to change; {@code null} is {@code INVALID_REQUEST}, without calling the API
     * @return the lesson after the change
     */
    ManagedVirtualLesson execute(String accessToken, String lessonId, VirtualLessonChanges changes);
}
