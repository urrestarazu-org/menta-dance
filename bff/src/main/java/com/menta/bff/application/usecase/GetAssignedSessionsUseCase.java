package com.menta.bff.application.usecase;

import java.util.List;

/**
 * Use case that lists the sessions the signed-in student holds, for the payment return page.
 * It is decoration only: it never fails, an unavailable lookup reads as no sessions.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface GetAssignedSessionsUseCase {

    /**
     * Lists the student's assigned sessions, soonest first.
     *
     * @param accessToken caller's access token
     * @return the sessions, empty when there are none or the lookup is unavailable
     */
    List<AssignedSessionRow> execute(String accessToken);
}
