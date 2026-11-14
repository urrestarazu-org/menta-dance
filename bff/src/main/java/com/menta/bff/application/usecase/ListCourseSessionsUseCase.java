package com.menta.bff.application.usecase;

import java.time.LocalDate;
import java.util.List;

/**
 * Use case that lists the sessions of a course, in every status, for the management screens (#368).
 *
 * <p>Part of Clean Architecture application layer. Failures of the API reach the caller as
 * {@code PhysicalCourseManagementApiClient.CourseManagementException} or
 * {@code PhysicalCourseManagementApiClient.ServiceUnavailableException}.
 * </p>
 */
public interface ListCourseSessionsUseCase {

    /**
     * Lists the sessions of a course, in the academy's days.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course; one that is not a UUID is a course that does not exist
     * @param localFrom   first day to list, in the academy's zone, inclusive; {@code null} for the API's default
     * @param localTo     last day to list, in the academy's zone, inclusive; {@code null} for the API's default
     * @return the sessions, worded in the academy's zone, possibly empty
     */
    List<ManagedSessionView> execute(String accessToken, String courseId, LocalDate localFrom, LocalDate localTo);
}
