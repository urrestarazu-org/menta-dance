package com.menta.auth.application.contract;

/**
 * Canonical auth-side outbox event types.
 *
 * Stable dotted paths. Consumers (PR-cross-module future) match on these
 * strings, so they MUST NOT change between releases without a migration note.
 */
public final class AuthOutboxEventTypes {

    public static final String AUTH_USER_LOGGED_IN = "auth.AuthUserLoggedIn";
    public static final String REFRESH_ROTATED = "auth.RefreshRotated";
    public static final String REFRESH_REVOKED = "auth.RefreshRevoked";
    public static final String USER_LOGGED_OUT = "auth.UserLoggedOut";

    private AuthOutboxEventTypes() {
        // Constant holder.
    }
}
