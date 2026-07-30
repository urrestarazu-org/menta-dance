package com.menta.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.TokenBlacklistPort;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * RED-GREEN discipline: this test references TokenBlacklistPortImpl BEFORE
 * 3.2 GREEN provides the impl, so the file must not compile until the impl
 * exists. The class implements BOTH:
 *   - TokenBlacklistPort (jti blacklist for revoked access tokens).
 *   - AuthDegradedGuard (fail-closed health check driven by the reconciler's
 *     last successful tick timestamp in Redis).
 *
 * Strict TDD: each test exercises a real branch of the production logic and
 * asserts concrete Redis interactions (key, value, TTL) so a regression in
 * the Redis contract would surface as a failing test, not as silent syndrome.
 */
@ExtendWith(MockitoExtension.class)
class TokenBlacklistPortImplTest {

    private static final String JTI = "44444444-4444-4444-4444-444444444444";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration DEGRADED_WINDOW = Duration.ofSeconds(30);

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private TokenBlacklistPortImpl port;

    @BeforeEach
    void setUp() {
        // Default stub wired leniently so tests that exercise only the
        // hasKey path do not trip Mockito strict-stubbing (PR1 finding).
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        port = new TokenBlacklistPortImpl(redisTemplate, DEGRADED_WINDOW.toSeconds());
    }

    @Nested
    @DisplayName("Spec: TokenBlacklistPort")
    class BlacklistOperations {

        @Test
        void blacklist_writes_jti_key_with_value_one_and_ttl_seconds() {
            port.blacklist(JTI, ACCESS_TTL);

            // Key must follow ADR-0026 convention `blacklist:jti:{jti}`.
            verify(valueOps).set(
                eq("blacklist:jti:" + JTI),
                eq("1"),
                eq(ACCESS_TTL)
            );
        }

        @Test
        void blacklist_is_idempotent_when_called_twice_with_same_jti() {
            port.blacklist(JTI, ACCESS_TTL);
            port.blacklist(JTI, ACCESS_TTL);

            // Both calls produce the same Redis SET; idempotency is a property
            // of Redis SET-with-TTL semantics, not adapter state. Asserting
            // both calls reach Redis proves the adapter does not short-circuit.
            verify(valueOps, times(2)).set(
                eq("blacklist:jti:" + JTI),
                eq("1"),
                eq(ACCESS_TTL)
            );
        }

        @Test
        void is_blacklisted_returns_true_when_redis_has_key() {
            when(redisTemplate.hasKey("blacklist:jti:" + JTI)).thenReturn(true);

            assertThat(port.isBlacklisted(JTI)).isTrue();
        }

        @Test
        void is_blacklisted_returns_false_when_redis_lacks_key() {
            when(redisTemplate.hasKey("blacklist:jti:" + JTI)).thenReturn(false);

            assertThat(port.isBlacklisted(JTI)).isFalse();
        }
    }

    @Nested
    @DisplayName("Spec: AuthDegradedGuard fail-closed")
    class DegradedGuard {

        @Test
        void returns_false_when_last_tick_fresh_enough() {
            long freshTick = Instant.now().minusSeconds(5).toEpochMilli();
            when(valueOps.get("auth:health:last_tick_at"))
                .thenReturn(String.valueOf(freshTick));

            AuthDegradedGuard guard = port;
            assertThat(guard.isDegraded()).isFalse();
        }

        @Test
        void returns_true_when_last_tick_older_than_window() {
            long staleTick = Instant.now().minus(DEGRADED_WINDOW.plusSeconds(1))
                .toEpochMilli();
            when(valueOps.get("auth:health:last_tick_at"))
                .thenReturn(String.valueOf(staleTick));

            AuthDegradedGuard guard = port;
            assertThat(guard.isDegraded()).isTrue();
        }

        @Test
        void returns_true_when_last_tick_missing() {
            // No reconciler has run yet — treat as degraded so login is blocked.
            when(valueOps.get("auth:health:last_tick_at")).thenReturn(null);

            AuthDegradedGuard guard = port;
            assertThat(guard.isDegraded()).isTrue();
        }

        @Test
        void returns_true_when_last_tick_corrupt() {
            when(valueOps.get("auth:health:last_tick_at"))
                .thenReturn("not-a-number");

            AuthDegradedGuard guard = port;
            assertThat(guard.isDegraded()).isTrue();
        }
    }

    @Nested
    @DisplayName("Spec: Fail-closed on Redis outage")
    class RedisOutage {

        @Test
        void blacklist_a_swallows_redis_failure_so_writers_do_not_crash() {
            // Write path: a Redis blip MUST NOT crash the producer. The
            // reconciler will retry on the next tick; lost side-effects are
            // recoverable from the durable outbox.
            doThrow(new RuntimeException("connection refused"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

            // No exception escapes the adapter — the call returns normally.
            port.blacklist(JTI, ACCESS_TTL);
        }

        @Test
        void is_blacklisted_returns_true_when_redis_throws_read() {
            // Read path: per ADR-0026, fail-closed. If we cannot prove the JTI
            // is safe, we MUST refuse the request.
            when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RuntimeException("connection refused"));

            assertThat(port.isBlacklisted(JTI)).isTrue();
        }

        @Test
        void degraded_guard_returns_true_when_redis_throws_tick_read() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString()))
                .thenThrow(new RuntimeException("connection refused"));

            AuthDegradedGuard guard = port;
            assertThat(guard.isDegraded()).isTrue();
        }

        @Test
        void blacklist_b_failure_does_not_call_redis_again() {
            doThrow(new RuntimeException("boom"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

            // Guard against retry storms on the blacklist path.
            port.blacklist(JTI, ACCESS_TTL);
            verify(valueOps, times(1)).set(anyString(), anyString(), any(Duration.class));
            verify(redisTemplate, never()).execute(any(RedisCallback.class));
        }
    }
}
