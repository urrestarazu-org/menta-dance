package com.menta.auth.application.port.out;

/**
 * Origin-based rate limiter guarding {@code reset-password} (US-AUTH-006 NFR:
 * "Máximo 10 intentos por IP por hora").
 *
 * <p>Single-dimension on purpose: unlike {@code forgot-password}, this
 * endpoint receives a token, not an email — there is no email to fingerprint
 * before the token is looked up, and looking one up just to rate-limit would
 * itself become an oracle. Budgeting by client origin only is what is
 * actually available at this boundary.</p>
 */
public interface PasswordResetAttemptRateLimitPort {

    /** @param clientFingerprint opaque, non-reversible fingerprint of the requesting client. */
    RateLimitDecision consume(String clientFingerprint);
}
