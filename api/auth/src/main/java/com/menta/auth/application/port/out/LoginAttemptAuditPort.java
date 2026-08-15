package com.menta.auth.application.port.out;

/**
 * Records the outcome of every login attempt (US-AUTH-002: "intentos fallidos
 * se limitan y auditan sin registrar secretos").
 *
 * <p><strong>Why this is not the outbox.</strong> {@link OutboxAppender}
 * writes inside the login transaction, which is exactly what makes it useless
 * here: a failed attempt raises {@code InvalidCredentialsException}, the
 * transaction rolls back, and the audit row disappears with it. The outbox
 * would faithfully record only the attempts nobody needs to investigate.
 * Implementations of this port must therefore write outside transactional
 * control.</p>
 *
 * <p>Implementations receive fingerprints only — never a raw email, IP,
 * password, or token — so the audit trail stays useful without becoming a
 * secondary source of the secrets it exists to protect.</p>
 */
public interface LoginAttemptAuditPort {

    /**
     * @param outcome what happened.
     * @param emailFingerprint opaque, non-reversible fingerprint of the target
     *                         email (never the raw address).
     * @param clientFingerprint opaque, non-reversible fingerprint of the
     *                          requesting client (never the raw IP).
     */
    void record(LoginAttemptOutcome outcome, String emailFingerprint, String clientFingerprint);
}
