package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualModule;
import com.menta.bff.application.dto.VirtualModuleChanges;

/**
 * Use case that changes a module from the edit form (#367). A {@code null} field of the changes stays as it is.
 *
 * <p>Part of Clean Architecture application layer. Only the token, the module id and the presence of the changes
 * are checked here. The length limit (title 255) and the per-field messages belong to the edit form, and the
 * business and ownership rules to the API: its refusals reach the caller as
 * {@code VirtualCourseManagementApiClient.VirtualCourseManagementException}. That is not an oversight.
 * </p>
 */
public interface UpdateVirtualModuleUseCase {

    /**
     * Changes the module.
     *
     * @param accessToken caller's access token
     * @param moduleId    identifier of the module; one that is not a UUID is a module that does not exist
     * @param changes     the fields to change; {@code null} is {@code INVALID_REQUEST}, without calling the API
     * @return the module after the change
     */
    ManagedVirtualModule execute(String accessToken, String moduleId, VirtualModuleChanges changes);
}
