package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link GetCourseSessionRosterUseCase}. Whether the caller may see the course, and who is in
 * the roster, are the Physical API's decisions; this class only orders the two calls and refuses a roster that
 * does not belong to the course in the URL.
 */
@RequiredArgsConstructor
public class GetCourseSessionRosterUseCaseImpl implements GetCourseSessionRosterUseCase {

    private final GetCourseSessionUseCase getCourseSessionUseCase;
    private final GetSessionRosterUseCase getSessionRosterUseCase;

    @Override
    public SessionRosterView execute(String accessToken, String courseId, String sessionId, String text) {
        ManagedSessionView session = getCourseSessionUseCase.execute(accessToken, courseId, sessionId);
        SessionRosterView roster = getSessionRosterUseCase.execute(accessToken, session.sessionId(), text);
        return switch (roster.outcome()) {
            case OK -> requireCourse(roster, session.courseId());
            case NOT_FOUND -> throw new CourseManagementException(Code.SESSION_NOT_FOUND);
            case UNAVAILABLE -> throw new ServiceUnavailableException("The roster of the session is unavailable");
        };
    }

    /** A roster that does not name the course, or names another one, is not shown. */
    private static SessionRosterView requireCourse(SessionRosterView roster, String courseId) {
        if (roster.courseId() == null || !roster.courseId().equalsIgnoreCase(courseId)) {
            throw new CourseManagementException(Code.SESSION_NOT_FOUND);
        }
        return roster;
    }
}
