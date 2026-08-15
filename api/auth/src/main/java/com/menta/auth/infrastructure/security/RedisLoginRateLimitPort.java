package com.menta.auth.infrastructure.security;

import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.domain.exception.AuthDegradedException;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis-backed semantic login budgets (ADR-0035, PR4).
 *
 * <p>Fixed windows over failure counts, in a key namespace separate from
 * activation's: burning activation attempts must never cost a user their
 * ability to log in, and vice versa.</p>
 *
 * <p>Both operations are single Lua round-trips. {@code check} reads without
 * mutating; {@code recordFailure} increments both counters together so a
 * failure can never be counted against one budget and not the other.</p>
 */
public final class RedisLoginRateLimitPort implements LoginRateLimitPort {

    private static final String EMAIL_KEY_PREFIX = "rate:auth-login:email:";
    private static final String CLIENT_KEY_PREFIX = "rate:auth-login:client:";

    /**
     * Read-only. Reports the remaining window when either budget is spent, so
     * the caller can answer with an honest {@code Retry-After}.
     */
    private static final RedisScript<List> CHECK_SCRIPT = new DefaultRedisScript<>("""
        local emailCount = tonumber(redis.call('GET', KEYS[1]) or '0')
        local clientCount = tonumber(redis.call('GET', KEYS[2]) or '0')
        if emailCount < tonumber(ARGV[1]) and clientCount < tonumber(ARGV[2]) then
            return {1, 0}
        end
        local emailTtl = redis.call('TTL', KEYS[1])
        local clientTtl = redis.call('TTL', KEYS[2])
        local remaining = 0
        if emailCount >= tonumber(ARGV[1]) and emailTtl > remaining then remaining = emailTtl end
        if clientCount >= tonumber(ARGV[2]) and clientTtl > remaining then remaining = clientTtl end
        return {0, remaining}
        """, List.class);

    /** Write-only. Both counters move together or not at all. */
    private static final RedisScript<List> RECORD_FAILURE_SCRIPT = new DefaultRedisScript<>("""
        local emailCount = redis.call('INCR', KEYS[1])
        if emailCount == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        local clientCount = redis.call('INCR', KEYS[2])
        if clientCount == 1 then redis.call('EXPIRE', KEYS[2], ARGV[1]) end
        return {emailCount, clientCount}
        """, List.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final long emailFailureLimit;
    private final long clientFailureLimit;
    private final Duration window;

    public RedisLoginRateLimitPort(
        RedisTemplate<String, String> redisTemplate,
        long emailFailureLimit,
        long clientFailureLimit,
        Duration window
    ) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate cannot be null");
        }
        if (emailFailureLimit <= 0 || clientFailureLimit <= 0 || window == null
            || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("failure limits and window must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.emailFailureLimit = emailFailureLimit;
        this.clientFailureLimit = clientFailureLimit;
        this.window = window;
    }

    @Override
    public RateLimitDecision check(String emailFingerprint, String clientFingerprint) {
        validateFingerprint(emailFingerprint, "emailFingerprint");
        validateFingerprint(clientFingerprint, "clientFingerprint");
        try {
            List<?> outcome = redisTemplate.execute(
                CHECK_SCRIPT,
                keys(emailFingerprint, clientFingerprint),
                String.valueOf(emailFailureLimit), String.valueOf(clientFailureLimit)
            );
            if (outcome == null || outcome.size() != 2
                || !(outcome.get(0) instanceof Number allowed)
                || !(outcome.get(1) instanceof Number ttl)) {
                throw new IllegalStateException("Invalid Redis login rate-limit response");
            }
            if (allowed.longValue() == 1) {
                return RateLimitDecision.allowed();
            }
            return RateLimitDecision.limited(Duration.ofSeconds(Math.max(1, ttl.longValue())));
        } catch (RuntimeException exception) {
            // Fail closed: an unavailable throttle must not silently become no
            // throttle.
            throw new AuthDegradedException();
        }
    }

    @Override
    public void recordFailure(String emailFingerprint, String clientFingerprint) {
        validateFingerprint(emailFingerprint, "emailFingerprint");
        validateFingerprint(clientFingerprint, "clientFingerprint");
        try {
            redisTemplate.execute(
                RECORD_FAILURE_SCRIPT,
                keys(emailFingerprint, clientFingerprint),
                String.valueOf(window.toSeconds())
            );
        } catch (RuntimeException exception) {
            throw new AuthDegradedException();
        }
    }

    /**
     * Best-effort by design: this runs <em>after</em> the access and refresh
     * tokens were issued and persisted. Failing closed here would answer 503
     * to a login that actually succeeded, with the refresh token already
     * written. A missed reset is self-correcting — the counter expires with
     * its window.
     */
    @Override
    public void resetEmail(String emailFingerprint) {
        validateFingerprint(emailFingerprint, "emailFingerprint");
        try {
            redisTemplate.delete(key(EMAIL_KEY_PREFIX, emailFingerprint));
        } catch (RuntimeException exception) {
            // Intentionally swallowed; the fixed window bounds the damage.
        }
    }

    private static List<String> keys(String emailFingerprint, String clientFingerprint) {
        return List.of(
            key(EMAIL_KEY_PREFIX, emailFingerprint),
            key(CLIENT_KEY_PREFIX, clientFingerprint)
        );
    }

    private static String key(String prefix, String fingerprint) {
        return prefix + "{" + fingerprint + "}";
    }

    private static void validateFingerprint(String fingerprint, String name) {
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 fingerprint");
        }
    }
}
