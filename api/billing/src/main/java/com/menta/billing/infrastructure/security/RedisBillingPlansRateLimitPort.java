package com.menta.billing.infrastructure.security;

import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.domain.exception.BillingDegradedException;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis-backed scraping budget for the public plans endpoints (US-BILLING-001:
 * 60 req/min per IP), mirroring {@code RedisLoginRateLimitPort}'s Lua-script
 * pattern in {@code auth}.
 *
 * <p>A single atomic INCR+EXPIRE per request — every request counts, there is
 * no "successful" outcome to exempt, unlike auth's login budget.</p>
 *
 * <p>Fail-closed: a Redis failure must not silently become "no limit" — an
 * unavailable throttle refuses the request rather than letting scraping
 * traffic through unbounded.</p>
 */
public final class RedisBillingPlansRateLimitPort implements BillingPlansRateLimitPort {

    private static final String KEY_PREFIX = "rate:billing-plans:ip:";

    private static final RedisScript<List> CONSUME_SCRIPT = new DefaultRedisScript<>("""
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        local ttl = redis.call('TTL', KEYS[1])
        if count > tonumber(ARGV[2]) then
            return {0, ttl}
        end
        return {1, 0}
        """, List.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final long requestLimit;
    private final Duration window;

    public RedisBillingPlansRateLimitPort(
        RedisTemplate<String, String> redisTemplate, long requestLimit, Duration window
    ) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate cannot be null");
        }
        if (requestLimit <= 0 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("requestLimit and window must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.requestLimit = requestLimit;
        this.window = window;
    }

    @Override
    public RateLimitDecision consume(String clientFingerprint) {
        validateFingerprint(clientFingerprint);
        try {
            List<?> outcome = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(KEY_PREFIX + clientFingerprint),
                String.valueOf(window.toSeconds()), String.valueOf(requestLimit)
            );
            if (outcome == null || outcome.size() != 2
                || !(outcome.get(0) instanceof Number allowed)
                || !(outcome.get(1) instanceof Number ttl)) {
                throw new IllegalStateException("Invalid Redis billing-plans rate-limit response");
            }
            if (allowed.longValue() == 1) {
                return RateLimitDecision.allowed();
            }
            return RateLimitDecision.limited(Duration.ofSeconds(Math.max(1, ttl.longValue())));
        } catch (RuntimeException exception) {
            // Fail closed: an unavailable throttle must not silently become no
            // throttle over a public, unauthenticated, scrapeable endpoint.
            throw new BillingDegradedException(exception);
        }
    }

    private static void validateFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("clientFingerprint cannot be null or blank");
        }
    }
}
