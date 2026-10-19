package com.menta.physical.infrastructure.redis;

import com.menta.physical.application.port.out.RedisLockPort;
import com.menta.physical.domain.exception.CheckInDegradedException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis-backed {@link RedisLockPort} for the door-side check-in lock
 * (US-PHYSICAL-001 escenario 6). Fail-closed, same criterion as {@code
 * RedisLoginRateLimitPort}: any {@link RuntimeException} Redis raises
 * becomes {@link CheckInDegradedException}, never a silent "no lock,
 * proceed anyway".
 *
 * <p>Unlike {@code RedisLoginRateLimitPort}, there is no Lua script here.
 * {@code SET key value NX EX ttl} — exposed by {@code
 * RedisTemplate#opsForValue()#setIfAbsent(key, value, ttl)} — is already a
 * single atomic Redis command; a script would only add complexity for no
 * extra safety on this one-key, one-operation lock.</p>
 */
public final class RedisCheckInLockPort implements RedisLockPort {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisCheckInLockPort(RedisTemplate<String, String> redisTemplate) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate cannot be null");
        }
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<String> acquireIfAbsent(String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key cannot be null or blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        String nonce = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, nonce, ttl);
            return Boolean.TRUE.equals(acquired) ? Optional.of(nonce) : Optional.empty();
        } catch (RuntimeException exception) {
            // Fail closed: an unavailable lock must not silently become no lock.
            throw new CheckInDegradedException();
        }
    }
}
