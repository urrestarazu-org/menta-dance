package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.UpdateSessionCommand;

/**
 * Use case that changes a session: reschedules it, changes its spots or notes, or cancels it (#368). A new date and
 * time arrive in the academy's zone and are sent to the API in UTC (ADR-0048). Capacity and date rules are the
 * API's: {@code CAPACITY_BELOW_ASSIGNED}, {@code SESSION_ALREADY_OCCURRED} and the rest reach the caller as
 * {@code CourseManagementException}.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface UpdateCourseSessionUseCase {

    /**
     * Changes the session.
     *
     * @param accessToken caller's access token
     * @param sessionId   identifier of the session; one that is not a UUID is a session that does not exist
     * @param command     the fields to change
     * @return the session after the change
     */
    ManagedSessionView execute(String accessToken, String sessionId, UpdateSessionCommand command);
}
