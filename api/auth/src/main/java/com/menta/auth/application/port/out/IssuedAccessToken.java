package com.menta.auth.application.port.out;

import java.time.Duration;

/**
 * Result of issuing an access token.
 *
 * @param token compact JWT string.
 * @param jti   UUID identifier embedded in the JWT. No outbox event is keyed on it
 *              any more (the AuthUserLoggedIn event was removed in #672).
 * @param ttl   time-to-live for cache/retry decisions.
 */
public record IssuedAccessToken(String token, String jti, Duration ttl) {
}
