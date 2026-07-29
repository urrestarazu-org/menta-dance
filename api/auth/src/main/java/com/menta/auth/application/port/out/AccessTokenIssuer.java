package com.menta.auth.application.port.out;

import com.menta.auth.domain.model.User;

import java.time.Duration;

/**
 * Port that issues short-lived JWT access tokens (ADR-0025).
 *
 * Infrastructure adapter (PR2) signs with HS256 secret loaded from env, embeds
 * `jti` UUID, `tokenVersion` claim, `sub/userId/role`, and `exp`.
 *
 * The issuer MUST be deterministic given the same user + invocation time —
 * callers can use it as the `aggregate_id` for AuthUserLoggedIn outbox events
 * (jti ties the event to the side-effect the reconciler later projects).
 */
public interface AccessTokenIssuer {

    /**
     * @return issuedAccessToken with token (compact JWT), jti (UUID, unique
     *         per token), and ttl (time-to-live steering client cache).
     */
    IssuedAccessToken issue(User user);

    /**
     * Returns the configured token TTL — handy for tests asserting behaviour
     * without coupling to wire format.
     */
    Duration ttl();
}
