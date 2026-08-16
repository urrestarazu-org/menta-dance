package com.menta.auth.application.contract;

/**
 * Canonical event type constants for the auth module's outbox events (ADR-0030).
 *
 * <h2>Why constants instead of an enum?</h2>
 * <p>
 * Event types are stored as strings in the database ({@code event_type} column)
 * and matched by consumers. Using {@code String} constants allows:
 * </p>
 * <ul>
 *   <li>Direct use in annotations (e.g., {@code @ConditionalOnProperty})</li>
 *   <li>Easy serialization without enum ordinal issues</li>
 *   <li>Pattern matching in SQL queries ({@code WHERE event_type LIKE 'auth.%'})</li>
 * </ul>
 *
 * <h2>Naming convention</h2>
 * <p>
 * Event types follow the pattern {@code <module>.<EventName>}:
 * </p>
 * <ul>
 *   <li>{@code auth.AuthUserLoggedIn} — user successfully authenticated</li>
 *   <li>{@code auth.RefreshRotated} — refresh token was rotated (new family)</li>
 *   <li>{@code auth.RefreshRevoked} — refresh token was explicitly revoked</li>
 *   <li>{@code auth.UserLoggedOut} — user logged out (all tokens invalidated)</li>
 * </ul>
 *
 * <h2>Stability contract</h2>
 * <p>
 * These values are <b>part of the public API</b>. Consumers (including future
 * cross-module listeners) match on these exact strings. Changing them requires
 * a migration strategy: either dual-write during transition or a database
 * migration to update existing rows.
 * </p>
 *
 * @see com.menta.auth.application.port.out.OutboxAppender
 */
public final class AuthOutboxEventTypes {

    public static final String AUTH_USER_LOGGED_IN = "auth.AuthUserLoggedIn";
    public static final String REFRESH_ROTATED = "auth.RefreshRotated";
    public static final String REFRESH_REVOKED = "auth.RefreshRevoked";
    public static final String USER_LOGGED_OUT = "auth.UserLoggedOut";

    /**
     * Durable delivery request for a pending account's activation email
     * (auth-account-activation spec: "Dispatch seguro del outbox"). The
     * outbox row carries only {@code activationTokenId} — never the raw
     * token — per design.md "Dispatch del outbox".
     */
    public static final String ACCOUNT_ACTIVATION_REQUESTED = "auth.AccountActivationRequested";

    /**
     * Durable delivery request for a password-reset email (US-AUTH-005). Same
     * shape and rationale as {@link #ACCOUNT_ACTIVATION_REQUESTED}: the row
     * carries only {@code passwordResetTokenId} — never the raw token.
     */
    public static final String PASSWORD_RESET_REQUESTED = "auth.PasswordResetRequested";

    /**
     * A password reset actually completed (US-AUTH-006 / #88 follow-up). The
     * domain method that changes the password also bumps the user's
     * tokenVersion in the same call, but that in-memory bump is invisible to
     * Redis until something publishes it through the outbox — this event is
     * that publication, carrying {@code userId} and {@code newTokenVersion}
     * so {@code TokenVersionOutboxEventHandler} can project it.
     */
    public static final String PASSWORD_RESET_COMPLETED = "auth.PasswordResetCompleted";

    private AuthOutboxEventTypes() {
        // Constant holder.
    }
}
