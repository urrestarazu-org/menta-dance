package com.menta.shared.auth;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read contract through which Physical resolves the names staff see on a session roster (#355).
 *
 * <p>Deliberately the smallest contract that answers the question: a display name per user id, in one
 * batch. It never returns a {@code User}, a role, a status or a contact detail, and it is meant for
 * staff-facing views only: the name falls back to the email when the account has no profile.</p>
 */
public interface UserDisplayNamePort {

    /**
     * Resolves the display names of the given users in a single batch, however many ids are asked.
     *
     * <p>The result is immutable and holds only the users that exist, keyed by their id; an unknown id is
     * simply absent, never an error. Duplicate and {@code null} ids are ignored, and an empty input returns
     * an empty map without any lookup. The name is the first and last name of the profile and, for an
     * account without one, its email. The method throws only for an infrastructure failure.</p>
     *
     * @param userIds the users to resolve; must not be {@code null}
     * @return the display name of each existing user, never {@code null}
     * @throws NullPointerException if {@code userIds} is {@code null}
     */
    Map<UUID, String> displayNamesOf(Collection<UUID> userIds);
}
