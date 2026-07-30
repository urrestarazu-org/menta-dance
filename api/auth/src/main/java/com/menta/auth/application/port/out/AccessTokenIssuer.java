package com.menta.auth.application.port.out;

import com.menta.auth.domain.model.User;

import java.time.Duration;
import java.util.Optional;

/**
 * Port that issues AND verifies short-lived JWT access tokens (ADR-0025).
 *
 * Infrastructure adapter (PR2) signs with HS256 secret loaded from env, embeds
 * `jti` UUID, `tokenVersion` claim, `sub/userId/role`, and `exp`. The same
 * adapter parses incoming bearer tokens during request authentication.
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

    /**
     * Parse and verify a compact JWT string. Returns the parsed claims when
     * the signature, expiration and required-claim set are all valid; empty
     * Optional when the token is malformed, expired, or has an unverifiable
     * signature. The parser MUST reject expired tokens so the failure-closed
     * path stays defensive.
     *
     * @param compact compact JWT string from the Authorization header.
     */
    Optional<ParsedAccessToken> parse(String compact);

    /**
     * Parsed claims projection. Domain-side consumers translate this into
     * Spring Authentication / Authorization entities.
     *
     * @param userId  UUID subject (String per JWT convention).
     * @param role    canonical role identifier (STUDENT / INSTRUCTOR / ADMIN).
     * @param tokenVersion numeric snapshot used to invalidate stale tokens.
     * @param jti     unique identifier for blacklist cross-reference.
     */
    record ParsedAccessToken(
        java.util.UUID userId,
        com.menta.auth.domain.model.Role role,
        long tokenVersion,
        String jti
    ) {
    }
}
