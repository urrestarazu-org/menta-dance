package com.menta.auth.domain.model;

/**
 * User account status in the Menta Dance platform.
 *
 * <p>Each status determines what actions the user can perform and how the system
 * responds to authentication attempts.
 *
 * <ul>
 *   <li><b>ACTIVE</b> — The user has a valid, operational account. Can log in,
 *       access courses, attend classes, and perform all actions permitted by their
 *       {@link Role}. This is the default status for newly registered users.</li>
 *
 *   <li><b>INACTIVE</b> — The user voluntarily deactivated their account or let their
 *       subscription lapse. Cannot log in. Data is retained for potential reactivation.
 *       Typical use: student on hiatus, expired trial, voluntary account closure.</li>
 *
 *   <li><b>SUSPENDED</b> — Temporary administrative restriction due to policy violation
 *       or pending investigation (e.g., payment dispute, terms of service review).
 *       Cannot log in. The account may be reactivated after resolution or escalated
 *       to LOCKED if the issue is confirmed.</li>
 *
 *   <li><b>LOCKED</b> — Permanent administrative lock due to confirmed abuse, fraud,
 *       or security compromise. Cannot log in. Logins fail-closed by raising
 *       {@link com.menta.auth.domain.exception.LockedUserException} without writing
 *       to the outbox (no event leakage for locked accounts).</li>
 * </ul>
 *
 * <p>State transitions are managed by domain services and require appropriate
 * authorization (typically ADMIN role).
 *
 * @see Role
 * @see com.menta.auth.domain.exception.LockedUserException
 */
public enum UserStatus {

    /**
     * Active account with full access according to user's role.
     */
    ACTIVE,

    /**
     * Voluntarily deactivated or subscription lapsed. Reactivation possible.
     */
    INACTIVE,

    /**
     * Temporarily restricted pending investigation. May be reactivated or escalated.
     */
    SUSPENDED,

    /**
     * Permanently locked due to abuse or security compromise. Login attempts fail-closed.
     */
    LOCKED
}
