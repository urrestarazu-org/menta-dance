package com.menta.bff.application.usecase;

/**
 * Use case that registers a student's attendance by hand, for the reception screen (#373).
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface RegisterManualCheckInUseCase {

    /**
     * Registers the attendance of a student in a session. Whether the caller may, whether the student has a spot
     * and whether the session accepts it is decided by the Physical API from the token; this class only refuses
     * what can never be sent (a missing access token, identifiers that are not UUIDs) and translates the
     * port's failures into results. Repeating it is safe: the API answers with the attendance that exists.
     *
     * @param accessToken caller's access token
     * @param sessionId   identifier of the session, as submitted
     * @param studentId   identifier of the student, as submitted
     * @return how the attempt ended; it never throws
     */
    ManualCheckInResult execute(String accessToken, String sessionId, String studentId);
}
