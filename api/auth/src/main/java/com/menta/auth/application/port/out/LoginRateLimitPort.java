package com.menta.auth.application.port.out;

/**
 * Semantic login budgets: failures per email and failures per origin
 * (ADR-0035, PR4).
 *
 * <p>Deliberately split into a read and a write. A single {@code consume}
 * operation would have to run before credentials are verified — which is the
 * only place a throttle can protect bcrypt — and would therefore count every
 * attempt, successful ones included. That is the defect this contract exists
 * to prevent: a shared origin counter that fills up on ordinary traffic stops
 * distinguishing "many legitimate users" from "one attacker", and eventually
 * locks out the very people it was meant to protect.</p>
 *
 * <p>Volumetric protection is not this port's job. NGINX caps request rate per
 * origin at the edge, before any of this runs. What remains here is the part
 * only the authentication layer can judge: whether an attempt actually
 * failed.</p>
 *
 * <p>The read/write split accepts a bounded race — concurrent requests can all
 * observe an under-budget counter before any of them records a failure. The
 * edge cap bounds how large that burst can get, which is precisely why the
 * volumetric limit had to land before this one.</p>
 *
 * <p>This throttle never changes account state. It must not transition a user
 * to {@code LOCKED}: escalating to a persistent lock would let any anonymous
 * caller deny a victim access by spraying junk passwords at their address.
 * {@code LOCKED} stays reserved for administrative action.</p>
 */
public interface LoginRateLimitPort {

    /**
     * Reports whether either budget is already exhausted, without consuming
     * anything.
     *
     * <p>Called before the repository lookup and before bcrypt, so a throttled
     * caller costs one Redis round-trip instead of a query plus an
     * intentionally slow hash comparison.</p>
     *
     * @param emailFingerprint opaque, non-reversible fingerprint of the target
     *                         email (never the raw address).
     * @param clientFingerprint opaque, non-reversible fingerprint of the
     *                          requesting origin (never the raw IP).
     */
    RateLimitDecision check(String emailFingerprint, String clientFingerprint);

    /**
     * Records one countable authentication failure against both budgets.
     *
     * <p>Called only after the attempt has been judged, so successful logins
     * never consume budget. Infrastructure failures are not countable
     * failures — they say nothing about the credentials.</p>
     */
    void recordFailure(String emailFingerprint, String clientFingerprint);

    /**
     * Clears the email budget after a successful authentication, so someone
     * who mistyped a few times and then got it right starts clean.
     *
     * <p>Only the email budget. The origin budget deliberately survives:
     * clearing it on success would let an attacker holding one valid account
     * of their own wipe their origin counter at will and keep spraying other
     * addresses from the same place.</p>
     */
    void resetEmail(String emailFingerprint);
}
