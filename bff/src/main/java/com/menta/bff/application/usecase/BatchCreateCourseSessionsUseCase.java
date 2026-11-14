package com.menta.bff.application.usecase;

import java.time.LocalDate;
import java.util.List;

/**
 * Use case that creates the sessions of a course for a range of days (#368): one per day of the range that falls
 * on the course's weekday. The range arrives in the academy's zone and is sent to the API as UTC dates
 * (ADR-0048). It is all or nothing, and the API decides it: {@code SESSION_ALREADY_EXISTS} (with the conflicting
 * dates) and {@code INVALID_REQUEST} (inverted range, or no day on the course's weekday) reach the caller as
 * {@code CourseManagementException}.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface BatchCreateCourseSessionsUseCase {

    /**
     * Creates the sessions of the range.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course; one that is not a UUID, or that the caller does not see, is a
     *                    course that does not exist
     * @param localFrom   first day of the range, in the academy's zone, inclusive
     * @param localTo     last day of the range, in the academy's zone, inclusive
     * @return the sessions created
     */
    List<ManagedSessionView> execute(String accessToken, String courseId, LocalDate localFrom, LocalDate localTo);
}
