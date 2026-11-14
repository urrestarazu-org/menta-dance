package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.UpdateCourseCommand;

/**
 * Use case that changes a physical course from the management form (#368). A new weekday and time arrive in the
 * academy's zone and are sent to the API in UTC (ADR-0048). The instructor of a course cannot be changed.
 *
 * <p>Part of Clean Architecture application layer. Ownership and the rule against deactivating a course with
 * assigned sessions are the API's: its refusals reach the caller as {@code CourseManagementException}.
 * </p>
 */
public interface UpdateManagedCourseUseCase {

    /**
     * Changes the course.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course; one that is not a UUID is a course that does not exist
     * @param command     the fields to change
     * @return the course after the change
     */
    ManagedCourseView execute(String accessToken, String courseId, UpdateCourseCommand command);
}
