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

    private AuthOutboxEventTypes() {
        // Constant holder.
    }
}
