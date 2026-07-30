package com.menta.auth.application.port.out;

import java.time.Duration;

/**
 * Result of issuing an access token.
 *
 * @param token compact JWT string.
 * @param jti   UUID identifier embedded in the JWT. Becomes the outbox
 *              aggregate_id for AuthUserLoggedIn events.
 * @param ttl   time-to-live for cache/retry decisions.
 */
public record IssuedAccessToken(String token, String jti, Duration ttl) {
}
