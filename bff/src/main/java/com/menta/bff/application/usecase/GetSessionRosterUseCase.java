package com.menta.bff.application.usecase;

/**
 * Use case that gets the roster of one session, with an optional search by name, for the reception screen (#373).
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface GetSessionRosterUseCase {

    /**
     * Gets the students with a confirmed spot in the session and whether each already checked in. The search is
     * applied here, over the roster the API returned: it ignores case and accents, and every word typed must be
     * part of the name. An identifier that is not a UUID is answered as not found without calling the API. A
     * failure never throws: it reads as the outcome that names it.
     *
     * @param accessToken caller's access token
     * @param sessionId   identifier of the session, as submitted
     * @param text        search text, as submitted; blank means no search
     * @return the roster view
     */
    SessionRosterView execute(String accessToken, String sessionId, String text);
}
