package com.menta.auth.application.port.out;

/**
 * Fingerprint-based rate limiter guarding {@code forgot-password}
 * (US-AUTH-005). Mirrors {@link ActivationRateLimitPort}'s shape — every
 * accepted request does real work (token generation, email send) regardless
 * of whether the account exists, so the whole request is the unit to budget,
 * unlike login's check-then-record split which exists specifically to protect
 * bcrypt.
 *
 * <p>Deliberately a distinct port from {@link ActivationRateLimitPort} and from
 * {@link PasswordResetAttemptRateLimitPort}: burning one budget must never
 * cost a user their ability to use another unrelated flow.</p>
 */
public interface PasswordResetRequestRateLimitPort {

    /**
     * @param emailFingerprint opaque, non-reversible fingerprint of the target
     *                         email (never the raw address).
     * @param clientFingerprint opaque, non-reversible fingerprint of the
     *                          requesting client (never the raw IP).
     */
    RateLimitDecision consume(String emailFingerprint, String clientFingerprint);
}
