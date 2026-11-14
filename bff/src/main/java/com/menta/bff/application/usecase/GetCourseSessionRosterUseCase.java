package com.menta.bff.application.usecase;

/**
 * Use case that reads the roster of one session of a course, for the management screens (#368): the students with
 * a confirmed spot and their attendance, read only.
 *
 * <p>Part of Clean Architecture application layer. The roster endpoint of the API does not limit an instructor to
 * their own courses, so this use case first looks the session up in the course of the URL
 * ({@link GetCourseSessionUseCase}, which the API refuses for a course that is not the instructor's) and only then
 * reads the roster. The roster must also say it belongs to that course.
 * </p>
 */
public interface GetCourseSessionRosterUseCase {

    /**
     * Reads the roster of the session.
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course as it came in the URL
     * @param sessionId   identifier of the session as it came in the URL
     * @param text        search text over the student's names, as submitted; blank means no search
     * @return the roster view, always with outcome {@link SessionRosterView.Outcome#OK}
     * @throws com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException with
     *         {@code COURSE_NOT_FOUND} or {@code SESSION_NOT_FOUND} when an id is not a UUID (without calling the
     *         API), when the course's sessions do not include the session, or when the roster is not of the
     *         course; with the API's own code when it refuses the listing (for example {@code COURSE_NOT_OWNED})
     * @throws com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.ServiceUnavailableException when
     *         the API cannot be reached or answers something unusable
     */
    SessionRosterView execute(String accessToken, String courseId, String sessionId, String text);
}
