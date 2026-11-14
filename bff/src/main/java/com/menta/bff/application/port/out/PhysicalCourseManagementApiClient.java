package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.CourseChanges;
import com.menta.bff.application.dto.CourseCreation;
import com.menta.bff.application.dto.ManagedCourse;
import com.menta.bff.application.dto.ManagedSession;
import com.menta.bff.application.dto.SessionChanges;
import com.menta.bff.application.dto.SessionCreation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Port for the management of physical courses and their sessions (#368): the calls of the Physical API under
 * {@code /api/v1/admin/physical}. The roster of a session is read through {@link PhysicalReceptionApiClient}.
 *
 * <p>The calls are authenticated with the staff member's access token, which only ever travels in the
 * {@code Authorization} header: it is never logged nor put in an exception message. Neither are identifiers,
 * names, notes or any response or error body. Everything is in UTC (ADR-0048): converting from the academy's zone
 * is the job of the use cases.
 * </p>
 *
 * <p>Every method throws {@link CourseManagementException} when the API refuses the request with one of the
 * expected codes, and {@link ServiceUnavailableException} for every other failure: timeout, transport error,
 * 401, 429, 5xx, an unexpected status or code, or an unusable body.
 * </p>
 */
public interface PhysicalCourseManagementApiClient {

    /**
     * Lists the courses the caller manages: all of them for an administrator, the own ones for an instructor
     * ({@code GET /courses}).
     *
     * @param accessToken caller's access token
     * @return the courses, possibly empty
     */
    List<ManagedCourse> listCourses(String accessToken);

    /**
     * Creates a course ({@code POST /courses}, 201).
     *
     * @param accessToken caller's access token
     * @param creation    the course, with the recurrence in UTC and an explicit instructor
     * @return the course as created
     */
    ManagedCourse createCourse(String accessToken, CourseCreation creation);

    /**
     * Changes a course ({@code PATCH /courses/{courseId}}, 200).
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course, already validated as a UUID by the caller
     * @param changes     the fields to change
     * @return the course after the change
     */
    ManagedCourse updateCourse(String accessToken, String courseId, CourseChanges changes);

    /**
     * Lists the sessions of a course, in every status ({@code GET /courses/{courseId}/sessions}).
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course, already validated as a UUID by the caller
     * @param from        start of the range, inclusive; {@code null} for the API's default
     * @param to          end of the range; {@code null} for the API's default
     * @return the sessions, possibly empty
     */
    List<ManagedSession> listCourseSessions(String accessToken, String courseId, Instant from, Instant to);

    /**
     * Creates one session ({@code POST /courses/{courseId}/sessions}, 201).
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course, already validated as a UUID by the caller
     * @param creation    the session, with date and time in UTC
     * @return the session as created
     */
    ManagedSession createSession(String accessToken, String courseId, SessionCreation creation);

    /**
     * Creates one session per UTC date of the range that falls on the course's UTC weekday, all or nothing
     * ({@code POST /courses/{courseId}/sessions/batch}, 201).
     *
     * @param accessToken caller's access token
     * @param courseId    identifier of the course, already validated as a UUID by the caller
     * @param fromDate    first UTC date, inclusive
     * @param toDate      last UTC date, inclusive
     * @return the sessions created
     */
    List<ManagedSession> createSessionsInBatch(String accessToken, String courseId, LocalDate fromDate,
            LocalDate toDate);

    /**
     * Changes a session: reschedules it, changes its spots or notes, or cancels it
     * ({@code PATCH /sessions/{sessionId}}, 200).
     *
     * @param accessToken caller's access token
     * @param sessionId   identifier of the session, already validated as a UUID by the caller
     * @param changes     the fields to change
     * @return the session after the change
     */
    ManagedSession updateSession(String accessToken, String sessionId, SessionChanges changes);

    /**
     * The API refused the request with a code the screens word. {@code COURSE_NOT_OWNED} (403), the
     * {@code *_NOT_FOUND} codes (404), the {@code 400} codes and the {@code 409} codes are the only ones accepted;
     * anything else is a {@link ServiceUnavailableException}. Its message is for logs only.
     */
    class CourseManagementException extends RuntimeException {

        /** The codes of {@code Problem.code} the API answers on these endpoints. */
        public enum Code {
            /** 400: an administrator created a course without {@code professorId}. */
            PROFESSOR_REQUIRED,
            /** 400: an instructor sent the id of another instructor. */
            PROFESSOR_MISMATCH,
            /** 400: invalid body, inverted range, or a range with no date on the course's weekday. */
            INVALID_REQUEST,
            /** 403: an instructor does not own the course. */
            COURSE_NOT_OWNED,
            /** 404: the course does not exist. */
            COURSE_NOT_FOUND,
            /** 404: the session does not exist. */
            SESSION_NOT_FOUND,
            /** 409: the course cannot be deactivated, it has future sessions with confirmed assignments. */
            COURSE_HAS_ACTIVE_ASSIGNMENTS,
            /** 409: the course already has a session at one of the instants; see the conflicting dates. */
            SESSION_ALREADY_EXISTS,
            /** 409: the new capacity is below the spots already assigned. */
            CAPACITY_BELOW_ASSIGNED,
            /** 409: the session already took place. */
            SESSION_ALREADY_OCCURRED
        }

        private final Code code;
        private final List<Instant> conflictingDates;

        public CourseManagementException(Code code) {
            this(code, List.of());
        }

        /**
         * Builds a refusal with the instants that already have a session.
         *
         * @param code             what the API refused
         * @param conflictingDates UTC instants that already have a session, only for
         *                         {@link Code#SESSION_ALREADY_EXISTS}
         */
        public CourseManagementException(Code code, List<Instant> conflictingDates) {
            super("Physical API refused the request: " + code);
            this.code = code;
            this.conflictingDates = List.copyOf(conflictingDates);
        }

        public Code getCode() {
            return code;
        }

        /** The UTC instants that already have a session; empty for every code but {@code SESSION_ALREADY_EXISTS}. */
        public List<Instant> getConflictingDates() {
            return conflictingDates;
        }
    }

    /** The management data could not be obtained or changed. Its message is for logs only. */
    class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }

        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
