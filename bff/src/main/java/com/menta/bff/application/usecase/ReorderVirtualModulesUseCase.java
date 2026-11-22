package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualModule;
import java.util.List;

/**
 * Use case that reorders the modules of a virtual course (#367).
 *
 * <p>Part of Clean Architecture application layer. Only the shape of what can be sent is checked here; whether the
 * ids are exactly the modules of the course, and the ownership rules, are the API's to decide: its refusals reach
 * the caller as {@code VirtualCourseManagementApiClient.VirtualCourseManagementException}.
 * </p>
 */
public interface ReorderVirtualModulesUseCase {

    /**
     * Reorders the modules.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course; one that is not a UUID is a course that does not exist
     * @param moduleIds   the ids of all the modules of the course, in the new order. A {@code null} or empty list,
     *                    or an id that is not a UUID, is {@code INVALID_REQUEST}; a repeated id is
     *                    {@code MODULE_REORDER_MISMATCH}; neither calls the API
     * @return the modules in their new order
     */
    List<ManagedVirtualModule> execute(String accessToken, String courseId, List<String> moduleIds);
}
