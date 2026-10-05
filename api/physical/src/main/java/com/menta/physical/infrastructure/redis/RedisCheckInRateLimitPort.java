package com.menta.physical.infrastructure.redis;

import com.menta.physical.application.dto.RateLimitDecision;
import com.menta.physical.application.dto.RateLimitDimension;
import com.menta.physical.application.port.out.CheckInRateLimitPort;
import com.menta.physical.domain.exception.CheckInDegradedException;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis-backed QR check-in throttle (ADR-0044): two independent fixed-window budgets, one per
 * client IP and one per device id.
 *
 * <p>Each budget is its own atomic INCR+EXPIRE script call on its own single key, so they do not
 * share state. The IP budget is consumed first and, once exhausted, the device budget is not
 * touched: an origin that is already throttled cannot burn a victim device's budget by spoofing
 * its id. Unlike {@code RedisActivationRateLimitPort}, the decision also says which budget
 * tripped, so the rejection can be observed per dimension.</p>
 *
 * <p>Fail-closed: any Redis fault or malformed reply becomes {@link CheckInDegradedException},
 * never a silent "no limit". Keys carry only the SHA-256 fingerprints handed in by the web
 * adapter, never raw request values.</p>
 */
public final class RedisCheckInRateLimitPort implements CheckInRateLimitPort {

    private static final String IP_KEY_PREFIX = "rate:physical-checkin:ip:";
    private static final String DEVICE_KEY_PREFIX = "rate:physical-checkin:device:";
    private static final String FINGERPRINT_PATTERN = "[0-9a-f]{64}";

    private static final RedisScript<List> CONSUME_SCRIPT = new DefaultRedisScript<>(
        """
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        local ttl = redis.call('TTL', KEYS[1])
        if count > tonumber(ARGV[2]) then
            return {0, ttl}
        end
        return {1, 0}
        """, List.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final long ipLimit;
    private final long deviceLimit;
    private final Duration window;

    public RedisCheckInRateLimitPort(
        RedisTemplate<String, String> redisTemplate, long ipLimit, long deviceLimit,
        Duration window
    ) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate cannot be null");
        }
        if (ipLimit <= 0 || deviceLimit <= 0 || window == null || window.isZero()
            || window.isNegative()) {
            throw new IllegalArgumentException("rate limits and window must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.ipLimit = ipLimit;
        this.deviceLimit = deviceLimit;
        this.window = window;
    }

    @Override
    public RateLimitDecision consume(String clientFingerprint, String deviceFingerprint) {
        validateFingerprint(clientFingerprint, "clientFingerprint");
        validateFingerprint(deviceFingerprint, "deviceFingerprint");
        RateLimitDecision ip = consumeBudget(
            RateLimitDimension.IP, IP_KEY_PREFIX + "{" + clientFingerprint + "}", ipLimit
        );
        if (!ip.isAllowed()) {
            return ip;
        }
        return consumeBudget(
            RateLimitDimension.DEVICE, DEVICE_KEY_PREFIX + "{" + deviceFingerprint + "}",
            deviceLimit
        );
    }

    private RateLimitDecision consumeBudget(RateLimitDimension dimension, String key, long limit) {
        try {
            List<?> outcome = redisTemplate.execute(
                CONSUME_SCRIPT, List.of(key), String.valueOf(window.toSeconds()),
                String.valueOf(limit)
            );
            if (outcome == null || outcome.size() != 2
                || !(outcome.get(0) instanceof Number allowed)
                || !(outcome.get(1) instanceof Number ttl)) {
                throw new IllegalStateException("Invalid Redis check-in rate-limit response");
            }
            if (allowed.longValue() == 1) {
                return RateLimitDecision.allowed();
            }
            return RateLimitDecision.limited(
                dimension, Duration.ofSeconds(Math.max(1, ttl.longValue()))
            );
        } catch (RuntimeException exception) {
            // Fail closed: an unavailable throttle must not silently become no throttle.
            throw new CheckInDegradedException();
        }
    }

    private static void validateFingerprint(String fingerprint, String name) {
        if (fingerprint == null || !fingerprint.matches(FINGERPRINT_PATTERN)) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 fingerprint");
        }
    }
}
