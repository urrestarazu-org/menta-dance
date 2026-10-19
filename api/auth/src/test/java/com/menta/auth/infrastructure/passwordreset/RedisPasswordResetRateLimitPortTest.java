package com.menta.auth.infrastructure.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.domain.exception.AuthDegradedException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** Both password-reset limiters: request (email+client) and attempt (client only). */
@ExtendWith(MockitoExtension.class)
class RedisPasswordResetRateLimitPortTest {

    private static final String EMAIL_FP = "a".repeat(64);
    private static final String CLIENT_FP = "b".repeat(64);

    @Mock private RedisTemplate<String, String> redisTemplate;

    @Nested
    @DisplayName("Request limiter (forgot-password)")
    class RequestLimiter {

        private RedisPasswordResetRequestRateLimitPort port() {
            return new RedisPasswordResetRequestRateLimitPort(
                redisTemplate, 3, 10, Duration.ofHours(1)
            );
        }

        @Test
        void allows_while_both_budgets_are_below_threshold() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 0L));

            assertThat(port().consume(EMAIL_FP, CLIENT_FP).isAllowed()).isTrue();

            verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                    "rate:auth-password-reset-request:email:{" + EMAIL_FP + "}",
                    "rate:auth-password-reset-request:client:{" + CLIENT_FP + "}"
                )),
                eq("3"), eq("10"), eq("3600")
            );
        }

        @Test
        void uses_a_namespace_separate_from_activation_and_login() {
            // Burning an activation or login budget must never consume the
            // user's ability to recover their password.
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 0L));

            port().consume(EMAIL_FP, CLIENT_FP);

            verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                    "rate:auth-password-reset-request:email:{" + EMAIL_FP + "}",
                    "rate:auth-password-reset-request:client:{" + CLIENT_FP + "}"
                )),
                any(Object[].class)
            );
        }

        @Test
        void returns_retry_after_when_a_budget_is_spent() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 1800L));

            assertThat(port().consume(EMAIL_FP, CLIENT_FP).getRetryAfter())
                .isEqualTo(Duration.ofSeconds(1800));
        }

        @Test
        void fails_closed_when_redis_is_unavailable() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("connection refused"));

            assertThatThrownBy(() -> port().consume(EMAIL_FP, CLIENT_FP))
                .isInstanceOf(AuthDegradedException.class);
        }

        @Test
        void rejects_fingerprints_that_are_not_sha256_hex() {
            assertThatThrownBy(() -> port().consume("nope", CLIENT_FP))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Attempt limiter (reset-password)")
    class AttemptLimiter {

        private RedisPasswordResetAttemptRateLimitPort port() {
            return new RedisPasswordResetAttemptRateLimitPort(
                redisTemplate, 10, Duration.ofHours(1)
            );
        }

        @Test
        void allows_while_the_origin_budget_is_below_threshold() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 0L));

            assertThat(port().consume(CLIENT_FP).isAllowed()).isTrue();

            verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("rate:auth-password-reset-attempt:client:{" + CLIENT_FP + "}")),
                eq("10"), eq("3600")
            );
        }

        @Test
        void returns_retry_after_when_the_budget_is_spent() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 900L));

            assertThat(port().consume(CLIENT_FP).getRetryAfter())
                .isEqualTo(Duration.ofSeconds(900));
        }

        @Test
        void never_reports_zero_retry_after_for_a_limited_decision() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 0L));

            assertThat(port().consume(CLIENT_FP).getRetryAfter()).isEqualTo(Duration.ofSeconds(1));
        }

        @Test
        void fails_closed_when_redis_is_unavailable() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("connection refused"));

            assertThatThrownBy(() -> port().consume(CLIENT_FP))
                .isInstanceOf(AuthDegradedException.class);
        }
    }
}
