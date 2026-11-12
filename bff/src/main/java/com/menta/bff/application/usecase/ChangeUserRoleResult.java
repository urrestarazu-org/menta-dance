package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.UserRole;

/**
 * What came of an administrator's attempt to change the role of an account. A failed attempt is a result, not an
 * exception, so the screen can answer each outcome in its own words.
 *
 * @param outcome     how the attempt ended
 * @param displayName name of the account's owner, or its email when it has none; only for the changed outcomes
 * @param role        the role the account now has; only for the changed outcomes
 */
public record ChangeUserRoleResult(Outcome outcome, String displayName, UserRole role) {

    /** How the attempt ended. */
    public enum Outcome {
        /** The account has the role; its sessions were closed by the API if the role really changed. */
        CHANGED,
        /** As {@link #CHANGED}, and the account is the signed-in administrator's own: their session is gone. */
        CHANGED_OWN_ROLE,
        /** The account does not exist. */
        USER_NOT_FOUND,
        /** The change would leave the system without an active administrator. */
        LAST_ADMIN,
        /** The data was refused, before or by the API. */
        INVALID,
        /** The API could not be reached or answered something unusable. */
        UNAVAILABLE
    }

    static ChangeUserRoleResult changed(Outcome outcome, String displayName, UserRole role) {
        return new ChangeUserRoleResult(outcome, displayName, role);
    }

    static ChangeUserRoleResult of(Outcome outcome) {
        return new ChangeUserRoleResult(outcome, null, null);
    }
}
