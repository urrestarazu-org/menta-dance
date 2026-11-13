package com.menta.auth.application.port.in;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Application-layer entry point through which other modules resolve how to show users to staff (#355).
 *
 * <p>It is the only thing of {@code auth} that the roster of a physical session sees: a name per user id,
 * never the user itself.
 */
public interface ResolveUserDisplayNamesUseCase {

    /**
     * Resolves the display names of the given users in one batch.
     *
     * <p>The name is the first and last name of the profile and, for an account that has none (demo, admin or
     * legacy rows), its email. {@code null} and repeated ids are ignored and an unknown id is simply absent
     * from the result, which is immutable.
     *
     * @param userIds the users to resolve; must not be {@code null}
     * @return the display name of every existing user, keyed by its id
     * @throws NullPointerException when {@code userIds} is {@code null}
     */
    Map<UUID, String> resolve(Collection<UUID> userIds);
}
