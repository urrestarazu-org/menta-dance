package com.menta.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.domain.exception.AuthDegradedException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisLoginRateLimitPortTest {

    private static final String EMAIL_FINGERPRINT = "a".repeat(64);
    private static final String CLIENT_FINGERPRINT = "b".repeat(64);
    private static final String EMAIL_KEY = "rate:auth-login:email:{" + EMAIL_FINGERPRINT + "}";
    private static final String CLIENT_KEY = "rate:auth-login:client:{" + CLIENT_FINGERPRINT + "}";

    @Mock private RedisTemplate<String, String> redisTemplate;
    private RedisLoginRateLimitPort port;

    @BeforeEach
    void setUp() {
        port = new RedisLoginRateLimitPort(redisTemplate, 10, 50, Duration.ofMinutes(15));
    }

    @Nested
    @DisplayName("check — read-only")
    class Check {

        @Test
        void allows_while_both_failure_budgets_are_below_threshold() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 0L));

            assertThat(port.check(EMAIL_FINGERPRINT, CLIENT_FINGERPRINT).isAllowed()).isTrue();

            verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(EMAIL_KEY, CLIENT_KEY)),
                eq("10"), eq("50")
            );
        }

        @Test
        void never_mutates_a_counter() {
            // The whole point of splitting check from recordFailure: a read
            // must not charge anything, or successful logins would consume
            // budget again — the defect ADR-0035 set out to fix.
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 0L));

            port.check(EMAIL_FINGERPRINT, CLIENT_FINGERPRINT);

            verify(redisTemplate, never()).opsForValue();
            verify(redisTemplate, never()).delete(any(String.class));
        }

        @Test
        void reports_the_remaining_window_when_a_budget_is_spent() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 57L));

            assertThat(port.check(EMAIL_FINGERPRINT, CLIENT_FINGERPRINT).getRetryAfter())
                .isEqualTo(Duration.ofSeconds(57));
        }

        @Test
        void never_reports_a_zero_retry_after_for_a_limited_decision() {
            // retryAfter=0 would invite an immediate retry, turning the
            // throttle into a busy-loop invitation.
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 0L));

            assertThat(port.check(EMAIL_FINGERPRINT, CLIENT_FINGERPRINT).getRetryAfter())
                .isEqualTo(Duration.ofSeconds(1));
        }

        @Test
        void fails_closed_when_redis_is_unavailable() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("connection refused"));

            assertThatThrownBy(() -> port.check(EMAIL_FINGERPRINT, CLIENT_FINGERPRINT))
                .isInstanceOf(AuthDegradedException.class);
        }

        @Test
        void fails_closed_on_a_malformed_script_response() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L));

            assertThatThrownBy(() -> port.check(EMAIL_FINGERPRINT, CLIENT_FINGERPRINT))
                .isInstanceOf(AuthDegradedException.class);
        }
    }

    @Nested
    @DisplayName("recordFailure — write-only")
    class RecordFailure {

        @Test
        void charges_both_budgets_in_one_round_trip() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 1L));

            port.recordFailure(EMAIL_FINGERPRINT, CLIENT_FINGERPRINT);

            // One script, both keys: a failure can never be charged to one
            // budget and not the other.
            verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(EMAIL_KEY, CLIENT_KEY)),
                eq("900")
            );
        }

        @Test
        void fails_closed_when_redis_is_unavailable() {
            when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("connection refused"));

            assertThatThrownBy(() -> port.recordFailure(EMAIL_FINGERPRINT, CLIENT_FINGERPRINT))
                .isInstanceOf(AuthDegradedException.class);
        }
    }

    @Nested
    @DisplayName("resetEmail")
    class ResetEmail {

        @Test
        void clears_only_the_email_key() {
            port.resetEmail(EMAIL_FINGERPRINT);

            verify(redisTemplate).delete(EMAIL_KEY);
            verify(redisTemplate, never()).delete(CLIENT_KEY);
        }

        @Test
        void is_best_effort_and_never_fails_a_successful_login() {
            // Tokens are already issued and the refresh row persisted by now.
            // Propagating a Redis failure would answer 503 to a login that
            // actually succeeded.
            when(redisTemplate.delete(EMAIL_KEY)).thenThrow(new RuntimeException("connection refused"));

            assertThatCode(() -> port.resetEmail(EMAIL_FINGERPRINT)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Input and configuration")
    class InputAndConfiguration {

        @Test
        void uses_a_key_namespace_separate_from_activation() {
            // Sharing a namespace would let a burst of activation attempts
            // spend the budget a user needs to sign in.
            assertThat(EMAIL_KEY).doesNotContain("auth-activation");
            assertThat(CLIENT_KEY).doesNotContain("auth-activation");
        }

        @Test
        void rejects_fingerprints_that_are_not_sha256_hex() {
            assertThatThrownBy(() -> port.check("not-a-fingerprint", CLIENT_FINGERPRINT))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> port.recordFailure(EMAIL_FINGERPRINT, "192.168.1.10"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> port.resetEmail(null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejects_a_non_positive_budget_or_window_at_construction() {
            assertThatThrownBy(() ->
                new RedisLoginRateLimitPort(redisTemplate, 0, 50, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() ->
                new RedisLoginRateLimitPort(redisTemplate, 10, 50, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() ->
                new RedisLoginRateLimitPort(null, 10, 50, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
