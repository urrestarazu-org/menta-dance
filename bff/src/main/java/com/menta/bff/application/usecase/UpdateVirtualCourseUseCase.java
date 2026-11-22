package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualCourse;
import com.menta.bff.application.dto.VirtualCourseChanges;

/**
 * Use case that changes a virtual course from the edit form (#367). A {@code null} field of the changes stays as
 * it is. The owner of a course cannot be changed.
 *
 * <p>Part of Clean Architecture application layer. Only the token, the id and the presence of the changes are
 * checked here. The length limits (title 255, short description 500, image URL 500, category 100) and the
 * per-field messages belong to the edit form, and the business and ownership rules to the API: its refusals
 * reach the caller as {@code VirtualCourseManagementApiClient.VirtualCourseManagementException}. That is not an
 * oversight.
 * </p>
 */
public interface UpdateVirtualCourseUseCase {

    /**
     * Changes the course.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course; one that is not a UUID is a course that does not exist
     * @param changes     the fields to change; {@code null} is {@code INVALID_REQUEST}, without calling the API
     * @return the course after the change
     */
    ManagedVirtualCourse execute(String accessToken, String courseId, VirtualCourseChanges changes);
}
