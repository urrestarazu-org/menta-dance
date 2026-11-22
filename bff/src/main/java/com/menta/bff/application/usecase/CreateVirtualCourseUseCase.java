package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CreateVirtualCourseCommand;
import com.menta.bff.application.dto.ManagedVirtualCourse;

/**
 * Use case that creates a virtual course, always as a draft (#367).
 *
 * <p>Part of Clean Architecture application layer. Only the token and the presence of the command and its level
 * are checked here. The length limits (title 255, short description 500, image URL 500, category 100) and the
 * per-field messages belong to the create form, and the business and ownership rules to the API: its refusals
 * reach the caller as {@code VirtualCourseManagementApiClient.VirtualCourseManagementException}. That is not an
 * oversight.
 * </p>
 */
public interface CreateVirtualCourseUseCase {

    /**
     * Creates the course.
     *
     * @param accessToken caller's access token
     * @param command     what the form submitted; a {@code null} command or level is {@code INVALID_REQUEST},
     *                    without calling the API
     * @return the created course
     */
    ManagedVirtualCourse execute(String accessToken, CreateVirtualCourseCommand command);
}
