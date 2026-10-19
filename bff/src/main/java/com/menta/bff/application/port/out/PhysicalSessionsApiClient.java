package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.AssignedSession;
import java.util.List;
import java.util.Set;

/**
 * Port for the signed-in student's own physical sessions (#542), used to mark on the class
 * page the sessions where the student already holds a confirmed spot, and listed on the payment
 * return page (#552).
 *
 * <p>The call is authenticated with the student's access token, which only ever travels in the
 * {@code Authorization} header: it is never logged nor put in an exception message.
 * </p>
 */
public interface PhysicalSessionsApiClient {

    /**
     * Lists the ids of the sessions where the caller holds a confirmed spot
     * ({@code GET /api/v1/physical/sessions/me}). The upstream decides the window of sessions
     * it returns and excludes holds, pending payments and cancelled sessions.
     *
     * @param accessToken caller's access token
     * @return the session ids, possibly empty
     * @throws ServiceUnavailableException for any failure: timeout, transport error, 401, 403, 404,
     *         5xx or an unusable body
     */
    Set<String> findMySessionIds(String accessToken);

    /**
     * Lists the sessions where the caller holds a confirmed spot, with the class name and start
     * ({@code GET /api/v1/physical/sessions/me}). Same upstream window and exclusions as
     * {@link #findMySessionIds(String)}. Items without an id or a start are skipped.
     *
     * @param accessToken caller's access token
     * @return the sessions in upstream order, possibly empty
     * @throws ServiceUnavailableException for any failure, as {@link #findMySessionIds(String)}
     */
    List<AssignedSession> findMySessions(String accessToken);

    /** The student's sessions could not be obtained. Its message is for logs only. */
    class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }

        public ServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
