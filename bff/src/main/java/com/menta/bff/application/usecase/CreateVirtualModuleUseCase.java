package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CreateVirtualModuleCommand;
import com.menta.bff.application.dto.ManagedVirtualModule;

/**
 * Use case that creates a module in a virtual course (#367).
 *
 * <p>Part of Clean Architecture application layer. Only the token, the course id and the presence of the command
 * are checked here. The length limit (title 255) and the per-field messages belong to the new-module form, and the
 * business and ownership rules to the API: its refusals reach the caller as
 * {@code VirtualCourseManagementApiClient.VirtualCourseManagementException}. That is not an oversight.
 * </p>
 */
public interface CreateVirtualModuleUseCase {

    /**
     * Creates the module.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course; one that is not a UUID is a course that does not exist
     * @param command     what the form submitted; {@code null} is {@code INVALID_REQUEST}, without calling the API
     * @return the created module
     */
    ManagedVirtualModule execute(String accessToken, String courseId, CreateVirtualModuleCommand command);
}
