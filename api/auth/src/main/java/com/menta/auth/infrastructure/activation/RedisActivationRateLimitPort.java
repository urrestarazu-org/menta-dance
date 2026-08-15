package com.menta.auth.infrastructure.activation;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.domain.exception.AuthDegradedException;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/** Atomic fixed-window rate limiter for activation register and resend flows. */
public final class RedisActivationRateLimitPort implements ActivationRateLimitPort {

    private static final String EMAIL_KEY_PREFIX = "rate:auth-activation:email:";
    private static final String CLIENT_KEY_PREFIX = "rate:auth-activation:client:";
    private static final RedisScript<List> CONSUME_SCRIPT = new DefaultRedisScript<>("""
        local emailCount = redis.call('INCR', KEYS[1])
        if emailCount == 1 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end
        local clientCount = redis.call('INCR', KEYS[2])
        if clientCount == 1 then redis.call('EXPIRE', KEYS[2], ARGV[3]) end
        local emailTtl = redis.call('TTL', KEYS[1])
        local clientTtl = redis.call('TTL', KEYS[2])
        if emailCount > tonumber(ARGV[1]) or clientCount > tonumber(ARGV[2]) then
            return {0, math.max(emailTtl, clientTtl)}
        end
        return {1, 0}
        """, List.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final long emailLimit;
    private final long clientLimit;
    private final Duration window;

    public RedisActivationRateLimitPort(
        RedisTemplate<String, String> redisTemplate,
        long emailLimit,
        long clientLimit,
        Duration window
    ) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate cannot be null");
        }
        if (emailLimit <= 0 || clientLimit <= 0 || window == null || window.isZero()
            || window.isNegative()) {
            throw new IllegalArgumentException("rate limits and window must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.emailLimit = emailLimit;
        this.clientLimit = clientLimit;
        this.window = window;
    }

    @Override
    public RateLimitDecision consume(String emailFingerprint, String clientFingerprint) {
        validateFingerprint(emailFingerprint, "emailFingerprint");
        validateFingerprint(clientFingerprint, "clientFingerprint");
        try {
            List<?> outcome = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(key(EMAIL_KEY_PREFIX, emailFingerprint), key(CLIENT_KEY_PREFIX, clientFingerprint)),
                String.valueOf(emailLimit), String.valueOf(clientLimit),
                String.valueOf(window.toSeconds())
            );
            if (outcome == null || outcome.size() != 2 || !(outcome.get(0) instanceof Number allowed)
                || !(outcome.get(1) instanceof Number ttl)) {
                throw new IllegalStateException("Invalid Redis activation rate-limit response");
            }
            if (allowed.longValue() == 1) {
                return RateLimitDecision.allowed();
            }
            return RateLimitDecision.limited(Duration.ofSeconds(Math.max(1, ttl.longValue())));
        } catch (RuntimeException exception) {
            throw new AuthDegradedException();
        }
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
