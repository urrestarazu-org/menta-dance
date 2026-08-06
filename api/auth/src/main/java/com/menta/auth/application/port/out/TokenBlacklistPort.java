package com.menta.auth.application.port.out;

import java.time.Duration;

/**
 * Cross-module port for the Redis JTI blacklist (ADR-0026).
 *
 * The outbox reconciler in :api:app projects PENDING AuthUserLoggedIn /
 * RefreshRevoked / UserLoggedOut events into Redis side-effects via this port.
 * Conversely, the LoginUseCase / RefreshTokenUseCase fail-closed guard depends
 * on the AuthDegradedGuard implementation that shares the same Redis client
 * and degraded-state derivation.
 *
 * Living as a Java interface in :api:auth application layer keeps the cross-
 * module contract explicit. The implementation in :api:auth infrastructure
 * layer uses Spring Data Redis (RedisTemplate) and is the single source for
 * the blacklist side-effect.
 */
public interface TokenBlacklistPort {

    /**
     * Mark a JTI as blacklisted for the given TTL. Idempotent — calling twice
     * with the same jti has the same observable effect as calling once.
     *
     * @param jti UUID string identifying the JWT.
     * @param ttl time-to-live for the blacklist entry; should match the access
     *            token's remaining lifetime so the entry naturally expires.
     */
    void blacklist(String jti, Duration ttl);

    /**
     * @return true if the JTI is currently blacklisted (and therefore the JWT
     *         must be rejected) or if Redis is unreachable (fail-closed under
     *         ADR-0026). False only when the entry is absent AND Redis is up.
     */
    boolean isBlacklisted(String jti);

    /**
     * Write a heartbeat timestamp to Redis indicating the reconciler's last
     * successful tick. AuthDegradedGuard uses this to determine if the system
     * is degraded. MUST propagate Redis failures to the caller so the
     * reconciler can detect write failures.
     */
    void writeHeartbeat();
}
