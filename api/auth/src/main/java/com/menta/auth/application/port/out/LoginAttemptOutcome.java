package com.menta.auth.application.port.out;

/**
 * Auditable result of a single login attempt.
 *
 * <p>Deliberately coarse. {@link #INVALID_CREDENTIALS} covers unknown email,
 * wrong password, and non-active account alike, mirroring the uniform 401 the
 * caller receives: the audit trail must not become the oracle that the HTTP
 * response refuses to be.</p>
 */
public enum LoginAttemptOutcome {

    /** Credentials verified and tokens issued. */
    SUCCESS,

    /** Unknown email, wrong password, or account not active. */
    INVALID_CREDENTIALS,

    /** Account is administratively locked (423). */
    LOCKED,

    /** Attempt rejected by the throttle before credentials were verified (429). */
    RATE_LIMITED
}
