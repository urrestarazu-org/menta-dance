package com.menta.auth.application.port.out;

/**
 * Cross-module port for the fingerprint-based rate limiter guarding register
 * and resend-activation (auth-account-activation spec: "Rate limiting").
 * Fail-closed 503 on backing-store unavailability is enforced by the
 * infrastructure adapter, not this contract.
 */
public interface ActivationRateLimitPort {

    /**
     * Consumes one attempt for the given fingerprints and returns whether
     * the caller may proceed.
     *
     * @param emailFingerprint opaque, non-reversible fingerprint of the
     *                         target email (never the raw address).
     * @param clientFingerprint opaque, non-reversible fingerprint of the
     *                           requesting client (never the raw IP).
     */
    RateLimitDecision consume(String emailFingerprint, String clientFingerprint);
}
