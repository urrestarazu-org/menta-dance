package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.AssignedSession;
import java.time.Instant;
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
     * ({@code GET /api/v1/physical/sessions/me}) whose start falls in the half-open range
     * {@code [from, to)}. The caller chooses the range, as UTC instants (ADR-0048); upstream rejects
     * a range with {@code from} not before {@code to} or longer than 92 days. Holds, pending payments and
     * cancelled sessions are excluded upstream.
     *
     * @param accessToken caller's access token
     * @param from        start of the range, inclusive
     * @param to          end of the range, exclusive
     * @return the session ids, possibly empty
     * @throws ServiceUnavailableException for any failure: timeout, transport error, 401, 403, 404,
     *         5xx or an unusable body
     */
    Set<String> findMySessionIds(String accessToken, Instant from, Instant to);

    /**
     * Lists the sessions where the caller holds a confirmed spot, with the class name and start
     * ({@code GET /api/v1/physical/sessions/me}). Same range and exclusions as
     * {@link #findMySessionIds(String, Instant, Instant)}. Items without an id or a start are skipped.
     *
     * @param accessToken caller's access token
     * @param from        start of the range, inclusive
     * @param to          end of the range, exclusive
     * @return the sessions in upstream order, possibly empty
     * @throws ServiceUnavailableException for any failure, as {@link #findMySessionIds(String, Instant, Instant)}
     */
    List<AssignedSession> findMySessions(String accessToken, Instant from, Instant to);

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
