package com.menta.auth.infrastructure.security;

import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.TokenBlacklistPort;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed implementation of TokenBlacklistPort + AuthDegradedGuard.
 *
 * Two responsibilities share one Redis client:
 *   1. JTI blacklist (TokenBlacklistPort): when an access token must be
 *      invalidated, the outbox reconciler (api:app / OutboxBlacklistReconciler)
 *      projects the event to Redis with key `blacklist:jti:{jti}` value `1`
 *      and TTL matching the access-token remaining lifetime.
 *   2. Reconciler liveness (AuthDegradedGuard): the reconciler writes a
 *      heartbeat key `auth:health:last_tick_at` after every batch tick.
 *      Auth flows fail-closed when the heartbeat is missing OR older than
 *      the degraded window (ADR-0026).
 *
 * Failure semantics:
 *   - Write path (blacklist): swallow Redis errors. The reconciler is
 *     idempotent and retries from the durable outbox; lost side-effects are
 *     recoverable on the next tick.
 *   - Read path (isBlacklisted / isDegraded): fail-CLOSED. If we cannot
 *     prove safety, we MUST refuse the request.
 */
@Component
public class TokenBlacklistPortImpl implements TokenBlacklistPort, AuthDegradedGuard {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistPortImpl.class);

    static final String BLACKLIST_KEY_PREFIX = "blacklist:jti:";
    static final String LAST_TICK_KEY = "auth:health:last_tick_at";

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration degradedWindow;

    public TokenBlacklistPortImpl(
        RedisTemplate<String, String> redisTemplate,
        @Value("${auth.outbox.degraded-window-seconds:30}") long degradedWindowSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.degradedWindow = Duration.ofSeconds(degradedWindowSeconds);
    }

    // ---- TokenBlacklistPort ----

    @Override
    public void blacklist(String jti, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(BLACKLIST_KEY_PREFIX + jti, "1", ttl);
        } catch (RuntimeException e) {
            // Write path: log + swallow. Reconciler retries from the durable
            // outbox on the next tick — no need to bubble up an exception.
            log.warn("blacklist write failed for jti={} ttl={} cause={}",
                jti, ttl, e.getMessage());
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti));
        } catch (RuntimeException e) {
            // Fail-closed per ADR-0026: we cannot prove the JTI is safe, so we
            // MUST refuse it.
            log.warn("blacklist read failed for jti={} cause={}", jti, e.getMessage());
            return true;
        }
    }

    // ---- AuthDegradedGuard ----

    @Override
    public boolean isDegraded() {
        String raw;
        try {
            raw = redisTemplate.opsForValue().get(LAST_TICK_KEY);
        } catch (RuntimeException e) {
            log.warn("last_tick read failed; degraded=true cause={}", e.getMessage());
            return true;
        }
        if (raw == null) {
            return true;
        }
        long lastTickMillis;
        try {
            lastTickMillis = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            log.warn("last_tick unparseable value={} degraded=true", raw);
            return true;
        }
        long nowMillis = System.currentTimeMillis();
        long ageMillis = nowMillis - lastTickMillis;
        return ageMillis >= degradedWindow.toMillis();
    }
}
