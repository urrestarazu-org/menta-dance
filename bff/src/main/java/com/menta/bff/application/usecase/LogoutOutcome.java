package com.menta.bff.application.usecase;

/**
 * Result of a logout attempt, so callers can report it honestly.
 *
 * <p>The local session is cleared in every case; this only tells whether the refresh
 * token was also revoked in the Auth API.
 * </p>
 */
public enum LogoutOutcome {

    /** The Auth API confirmed the revocation of the refresh token. */
    REVOKED,

    /** There was no session, so there was nothing to revoke. */
    NO_SESSION,

    /** Revocation could not be completed; the refresh token may still be valid in the Auth API. */
    REVOCATION_FAILED
}
