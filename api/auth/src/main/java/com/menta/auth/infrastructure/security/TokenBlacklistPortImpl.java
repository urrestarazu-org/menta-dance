package com.menta.auth.infrastructure.security;

import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.TokenBlacklistPort;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
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
 * Failure semantics (PR3):
 *   - Write path (blacklist, writeHeartbeat): PROPAGATE Redis errors. The
 *     reconciler MUST detect write failures to transition outbox rows to
 *     FAILED with retry backoff instead of marking them COMPLETED incorrectly.
 *   - Read path (isBlacklisted / isDegraded): fail-CLOSED. If we cannot
 *     prove safety, we MUST refuse the request.
 */
@Component
public class TokenBlacklistPortImpl implements TokenBlacklistPort, AuthDegradedGuard {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistPortImpl.class);

    static final String BLACKLIST_KEY_PREFIX = "blacklist:jti:";
    static final String TOKEN_VERSION_KEY_PREFIX = "auth:tokenversion:user:";
    static final String LAST_TICK_KEY = "auth:health:last_tick_at";

    /**
     * Monotonic write: outbox rows can reach here out of order (a FAILED
     * row's backoff retry racing a newer PENDING one, or a crash-resume
     * replay), and an unconditional SET would let a stale replay lower the
     * projected version below one already written — silently re-opening a
     * token that was already revoked (#88 follow-up). A read-then-write from
     * Java would race across two round-trips, so the compare-and-set must be
     * one atomic server-side script, mirroring the rate-limit ports'
     * pattern.
     */
    private static final RedisScript<Long> PROJECT_TOKEN_VERSION_SCRIPT = new DefaultRedisScript<>("""
        local current = tonumber(redis.call('GET', KEYS[1]))
        if (current == nil) or (tonumber(ARGV[1]) > current) then
            redis.call('SET', KEYS[1], ARGV[1])
            return 1
        end
        return 0
        """, Long.class);

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
        // PR3: Propagate Redis write failures. The reconciler MUST detect them
        // to transition outbox rows to FAILED with retry backoff.
        redisTemplate.opsForValue().set(BLACKLIST_KEY_PREFIX + jti, "1", ttl);
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

    @Override
    public void projectTokenVersion(String userId, long tokenVersion) {
        // No TTL: a blacklist entry may expire with its access token, but the
        // current version must outlive every token that could still present a
        // stale one. Failures propagate so the reconciler retries.
        redisTemplate.execute(
            PROJECT_TOKEN_VERSION_SCRIPT,
            List.of(TOKEN_VERSION_KEY_PREFIX + userId),
            Long.toString(tokenVersion)
        );
    }

    @Override
    public java.util.OptionalLong currentTokenVersion(String userId) {
        // Deliberately NOT caught here: unlike isBlacklisted, this method
        // cannot express "unsafe" through its return type — an empty result
        // legitimately means "never revoked". The caller decides, and the
        // filter refuses the request.
        String raw = redisTemplate.opsForValue().get(TOKEN_VERSION_KEY_PREFIX + userId);
        if (raw == null) {
            return java.util.OptionalLong.empty();
        }
        try {
            return java.util.OptionalLong.of(Long.parseLong(raw));
        } catch (NumberFormatException malformed) {
            // A corrupt projection must not read as "no revocation ever
            // happened" — that would silently re-open every revoked token.
            log.warn("malformed tokenVersion projection for userId={}", userId);
            throw new IllegalStateException("Malformed tokenVersion projection", malformed);
        }
    }

    @Override
    public void writeHeartbeat() {
        // PR3: Write reconciler heartbeat without TTL. Propagate failures so
        // the reconciler can detect Redis outages.
        long nowMillis = System.currentTimeMillis();
        redisTemplate.opsForValue().set(LAST_TICK_KEY, String.valueOf(nowMillis));
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
