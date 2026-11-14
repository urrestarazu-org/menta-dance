package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedCourse;
import com.menta.bff.application.dto.ManagedSession;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.usecase.ClassLabels.WeeklySlot;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** What the course and session management use cases share: argument checks and the view models. */
final class CourseManagementSupport {

    private static final String CANCELLED = "CANCELLED";
    private static final String ACTIVE = "ACTIVE";

    private CourseManagementSupport() {
    }

    /** A call without an access token cannot succeed: it reads as unavailable, without calling the API. */
    static String token(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ServiceUnavailableException("No access token for the Physical API");
        }
        return accessToken;
    }

    /** Only a canonical UUID reaches a path; anything else cannot be a course. */
    static String courseId(String courseId) {
        String id = UuidFormat.normalize(courseId);
        if (id == null) {
            throw new CourseManagementException(Code.COURSE_NOT_FOUND);
        }
        return id;
    }

    /** Only a canonical UUID reaches a path; anything else cannot be a session. */
    static String sessionId(String sessionId) {
        String id = UuidFormat.normalize(sessionId);
        if (id == null) {
            throw new CourseManagementException(Code.SESSION_NOT_FOUND);
        }
        return id;
    }

    static ManagedCourseView view(ManagedCourse course, ZoneId zone) {
        WeeklySlot local = ClassLabels.toLocalSlot(course.dayOfWeek(), course.startTime(), zone);
        return new ManagedCourseView(course.courseId(), course.title(), course.description(), course.professorId(),
                course.professorName(), local.day(), local.time(),
                ClassLabels.schedule(course.dayOfWeek().name(), course.startTime().toString(), zone),
                course.durationMinutes(), course.level(), course.capacity(), ACTIVE.equals(course.status()));
    }

    static ManagedSessionView view(ManagedSession session, ZoneId zone) {
        ZonedDateTime local = session.scheduledAt().atZone(zone);
        return new ManagedSessionView(session.sessionId(), session.courseId(), session.scheduledAt(),
                local.toLocalDate(), local.toLocalTime(), ClassLabels.moment(session.scheduledAt(), zone),
                CANCELLED.equals(session.status()), session.capacity(),
                session.assignedSpots() + session.activeCapacityHolds(), session.availableSpots(), session.notes());
    }
}
