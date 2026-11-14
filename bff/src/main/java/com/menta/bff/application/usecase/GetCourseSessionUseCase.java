package com.menta.bff.application.usecase;

/**
 * Use case that reads one session of a course, to confirm a change to it (#368): reschedule or cancel.
 *
 * <p>Part of Clean Architecture application layer. The API has no read-one endpoint for a session, so it is looked
 * for among the sessions it lists for the course (in every status, over its default window of years on both sides
 * of today): that also means a session of another course is never found through this course's URL, and an
 * instructor never reads a session of a course that is not theirs, because the API refuses the listing.
 * </p>
 */
public interface GetCourseSessionUseCase {

    /**
     * Reads the session.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course as it came in the URL
     * @param sessionId   identifier of the session as it came in the URL
     * @return the session, worded in the academy's zone
     * @throws com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException with
     *         {@code COURSE_NOT_FOUND} or {@code SESSION_NOT_FOUND} when an id is not a UUID, without calling the
     *         API, or when the course's sessions do not include it; with the API's own code when it refuses the
     *         listing (for example {@code COURSE_NOT_OWNED})
     */
    ManagedSessionView execute(String accessToken, String courseId, String sessionId);
}
