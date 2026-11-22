package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CreateVirtualLessonCommand;
import com.menta.bff.application.dto.ManagedVirtualLesson;

/**
 * Use case that creates a lesson in a module (#367).
 *
 * <p>Part of Clean Architecture application layer. Only the token, the module id and the presence of the command
 * are checked here. The length limits (title 255, description 65535 bytes, video id 128) and the per-field
 * messages belong to the new-lesson form, and the business and ownership rules to the API: its refusals reach the
 * caller as {@code VirtualCourseManagementApiClient.VirtualCourseManagementException}. That is not an oversight.
 * </p>
 */
public interface CreateVirtualLessonUseCase {

    /**
     * Creates the lesson.
     *
     * @param accessToken caller's access token
     * @param moduleId    identifier of the module; one that is not a UUID is a module that does not exist
     * @param command     what the form submitted; {@code null} is {@code INVALID_REQUEST}, without calling the API
     * @return the created lesson
     */
    ManagedVirtualLesson execute(String accessToken, String moduleId, CreateVirtualLessonCommand command);
}
