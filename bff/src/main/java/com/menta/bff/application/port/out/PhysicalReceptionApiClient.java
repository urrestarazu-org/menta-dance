package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.CheckInReceipt;
import com.menta.bff.application.dto.SessionRoster;
import com.menta.bff.application.dto.StaffSession;
import java.time.Instant;
import java.util.List;

/**
 * Port for the reception screen's calls to the Physical API (#373): the agenda of a range of days
 * ({@code GET /api/v1/admin/physical/sessions}), the roster of a session
 * ({@code GET /api/v1/admin/physical/sessions/{sessionId}/roster}) and the manual check-in
 * ({@code POST /api/v1/physical/sessions/{sessionId}/check-ins}, {@code MANUAL} variant).
 *
 * <p>The calls are authenticated with the staff member's access token, which only ever travels in the
 * {@code Authorization} header: it is never logged nor put in an exception message. Neither are the
 * identifiers of sessions and students, the names in the roster or any response or error body. The actor of
 * a check-in is the one the token names; the BFF only sends which student and which session.
 * </p>
 */
public interface PhysicalReceptionApiClient {

    /**
     * Lists the sessions that start in {@code [from, to)}, ordered by start and course name.
     *
     * @param accessToken caller's access token
     * @param from        start of the range, inclusive
     * @param to          end of the range, exclusive; at most 7 days after {@code from}
     * @return the sessions, possibly empty
     * @throws ServiceUnavailableException for any failure: timeout, transport error, 400, 401, 403, 429, 5xx,
     *                                     an unexpected status or an unusable body
     */
    List<StaffSession> listSessions(String accessToken, Instant from, Instant to);

    /**
     * Gets the students with a confirmed spot in a session and whether each already checked in.
     *
     * @param accessToken caller's access token
     * @param sessionId   identifier of the session, already validated as a UUID by the caller
     * @return the roster
     * @throws SessionNotFoundException    if the session does not exist (404 {@code SESSION_NOT_FOUND})
     * @throws ServiceUnavailableException for any other failure, as {@link #listSessions}
     */
    SessionRoster getRoster(String accessToken, String sessionId);

    /**
     * Registers the attendance of a student by hand. It is idempotent upstream: repeating it answers
     * {@code 200} with the attendance that already exists, which is a result, not a failure.
     *
     * @param accessToken caller's access token
     * @param sessionId   identifier of the session, already validated as a UUID by the caller
     * @param studentId   identifier of the student, already validated as a UUID by the caller
     * @return the attendance, flagged when it already existed
     * @throws CheckInRejectedException    if the API refuses it for a reason the screen words (403
     *                                     {@code CAPACITY_ASSIGNMENT_REQUIRED} or {@code OUTSIDE_CHECK_IN_WINDOW},
     *                                     409 {@code SESSION_NOT_ACTIVE})
     * @throws ServiceUnavailableException for any other failure: timeout, transport error, any other 400, 403,
     *                                     404 or 409, 401, 429, 5xx, an unexpected status, or a 2xx without a
     *                                     usable body (repeating the call is then harmless)
     */
    CheckInReceipt registerManualCheckIn(String accessToken, String sessionId, String studentId);

    /** The session does not exist (404 {@code SESSION_NOT_FOUND}). */
    class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(String message) {
            super(message);
        }
    }

    /** The API refused the check-in for a reason the screen words. Its message is for logs only. */
    class CheckInRejectedException extends RuntimeException {

        /** Why the check-in was refused. */
        public enum Reason {
            /** The student has no confirmed spot in the session (403 {@code CAPACITY_ASSIGNMENT_REQUIRED}). */
            CAPACITY_ASSIGNMENT_REQUIRED,
            /** The session is outside the check-in window (403 {@code OUTSIDE_CHECK_IN_WINDOW}). */
            OUTSIDE_CHECK_IN_WINDOW,
            /** The session is cancelled (409 {@code SESSION_NOT_ACTIVE}). */
            SESSION_NOT_ACTIVE
        }

        private final Reason reason;

        public CheckInRejectedException(Reason reason) {
            super("Physical API refused the check-in: " + reason);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }

    /** The reception data could not be obtained. Its message is for logs only. */
    class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }

        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
