package com.menta.auth.infrastructure.passwordreset;

import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.domain.exception.AuthDegradedException;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Atomic fixed-window rate limiter for {@code reset-password} (US-AUTH-006:
 * 10 attempts/hour per IP).
 *
 * <p>Single-dimension by necessity: this endpoint receives a token, not an
 * email, so there is no email to budget against before the token is resolved
 * — and resolving one just to rate-limit would turn the limiter itself into
 * an existence oracle.</p>
 */
public final class RedisPasswordResetAttemptRateLimitPort
    implements PasswordResetAttemptRateLimitPort {

    private static final String CLIENT_KEY_PREFIX = "rate:auth-password-reset-attempt:client:";

    private static final RedisScript<List> CONSUME_SCRIPT = new DefaultRedisScript<>("""
        local clientCount = redis.call('INCR', KEYS[1])
        if clientCount == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
        local clientTtl = redis.call('TTL', KEYS[1])
        if clientCount > tonumber(ARGV[1]) then
            return {0, clientTtl}
        end
        return {1, 0}
        """, List.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final long clientLimit;
    private final Duration window;

    public RedisPasswordResetAttemptRateLimitPort(
        RedisTemplate<String, String> redisTemplate,
        long clientLimit,
        Duration window
    ) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate cannot be null");
        }
        if (clientLimit <= 0 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("rate limit and window must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.clientLimit = clientLimit;
        this.window = window;
    }

    @Override
    public RateLimitDecision consume(String clientFingerprint) {
        validateFingerprint(clientFingerprint);
        try {
            List<?> outcome = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(CLIENT_KEY_PREFIX + "{" + clientFingerprint + "}"),
                String.valueOf(clientLimit), String.valueOf(window.toSeconds())
            );
            if (outcome == null || outcome.size() != 2
                || !(outcome.get(0) instanceof Number allowed)
                || !(outcome.get(1) instanceof Number ttl)) {
                throw new IllegalStateException(
                    "Invalid Redis password reset attempt rate-limit response"
                );
            }
            if (allowed.longValue() == 1) {
                return RateLimitDecision.allowed();
            }
            return RateLimitDecision.limited(Duration.ofSeconds(Math.max(1, ttl.longValue())));
        } catch (RuntimeException exception) {
            throw new AuthDegradedException();
        }
    }

    private static void validateFingerprint(String clientFingerprint) {
        if (clientFingerprint == null || !clientFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                "clientFingerprint must be a lowercase SHA-256 fingerprint"
            );
        }
    }
}
