package com.menta.bff.application.usecase;

/**
 * Use case that lists the sessions that start today, for the reception screen (#373).
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface ListTodaySessionsUseCase {

    /**
     * Lists the sessions of the academy's current day. "Today" is the local day in the academy's zone, sent to
     * the API as the UTC range {@code [start of the day, start of the next day)}. A failure never throws: it
     * reads as an unavailable view.
     *
     * @param accessToken caller's access token
     * @return the agenda of the day
     */
    ReceptionSessionsView execute(String accessToken);
}
