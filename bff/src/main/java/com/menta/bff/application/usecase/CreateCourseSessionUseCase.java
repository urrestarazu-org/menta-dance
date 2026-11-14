package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CreateSessionCommand;

/**
 * Use case that creates one session of a course (#368). The date and time arrive in the academy's zone and are
 * sent to the API in UTC (ADR-0048). Whether the course already has a session at that instant is the API's
 * decision: {@code SESSION_ALREADY_EXISTS} reaches the caller as {@code CourseManagementException}.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface CreateCourseSessionUseCase {

    /**
     * Creates the session.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course; one that is not a UUID is a course that does not exist
     * @param command     the session, in the academy's zone
     * @return the session as created
     */
    ManagedSessionView execute(String accessToken, String courseId, CreateSessionCommand command);
}
