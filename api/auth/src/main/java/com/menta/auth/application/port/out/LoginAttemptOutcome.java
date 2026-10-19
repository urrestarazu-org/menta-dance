package com.menta.auth.application.port.out;

/**
 * Auditable result of a single login attempt.
 *
 * <p>{@link #INVALID_CREDENTIALS} covers unknown email and wrong password alike,
 * mirroring the uniform 401 the caller receives: the audit trail must not become
 * the oracle that the HTTP response refuses to be. The account-state outcomes
 * ({@link #LOCKED}, {@link #ACCOUNT_NOT_ACTIVATED}, {@link #ACCOUNT_NOT_OPERATIONAL})
 * are recorded only after the password was verified, so they never reveal the
 * state of an account to someone who does not hold its credentials. The audit sink
 * is a structured log, so the enum is not persisted and needs no schema change.</p>
 */
public enum LoginAttemptOutcome {

    /** Credentials verified and tokens issued. */
    SUCCESS,

    /** Unknown email or wrong password, whatever the account status. */
    INVALID_CREDENTIALS,

    /** Correct password, but the account is administratively locked (423). */
    LOCKED,

    /** Correct password, but the account is still awaiting activation (403). */
    ACCOUNT_NOT_ACTIVATED,

    /**
     * Correct password, but the account is inactive or suspended. The caller still
     * receives the generic 401; this value exists only in the audit trail.
     */
    ACCOUNT_NOT_OPERATIONAL,

    /** Attempt rejected by the throttle before credentials were verified (429). */
    RATE_LIMITED
}
