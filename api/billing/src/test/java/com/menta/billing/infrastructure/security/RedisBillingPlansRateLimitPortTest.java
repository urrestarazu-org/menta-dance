package com.menta.billing.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.menta.billing.domain.exception.BillingDegradedException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisBillingPlansRateLimitPortTest {

    private static final String CLIENT_FINGERPRINT = "a".repeat(64);
    private static final String KEY = "rate:billing-plans:ip:" + CLIENT_FINGERPRINT;

    @Mock private RedisTemplate<String, String> redisTemplate;
    private RedisBillingPlansRateLimitPort port;

    @BeforeEach
    void setUp() {
        port = new RedisBillingPlansRateLimitPort(redisTemplate, 60, Duration.ofSeconds(60));
    }

    @Test
    void allows_while_the_budget_is_below_threshold() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of(1L, 0L));

        assertThat(port.consume(CLIENT_FINGERPRINT).isAllowed()).isTrue();

        verify().execute(any(RedisScript.class), eq(List.of(KEY)), eq("60"), eq("60"));
    }

    @Test
    void reports_the_remaining_window_when_the_budget_is_spent() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of(0L, 23L));

        assertThat(port.consume(CLIENT_FINGERPRINT).getRetryAfter()).isEqualTo(Duration.ofSeconds(23));
    }

    @Test
    void never_reports_a_zero_retry_after_for_a_limited_decision() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of(0L, 0L));

        assertThat(port.consume(CLIENT_FINGERPRINT).getRetryAfter()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void fails_closed_when_redis_is_unavailable() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> port.consume(CLIENT_FINGERPRINT))
            .isInstanceOf(BillingDegradedException.class);
    }

    @Test
    void fails_closed_on_a_malformed_script_response() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of(1L));

        assertThatThrownBy(() -> port.consume(CLIENT_FINGERPRINT))
            .isInstanceOf(BillingDegradedException.class);
    }

    @Test
    void rejects_a_null_or_blank_fingerprint() {
        assertThatThrownBy(() -> port.consume(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> port.consume(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_non_positive_limit_or_window_at_construction() {
        assertThatThrownBy(() -> new RedisBillingPlansRateLimitPort(redisTemplate, 0, Duration.ofSeconds(60)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisBillingPlansRateLimitPort(redisTemplate, 60, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisBillingPlansRateLimitPort(null, 60, Duration.ofSeconds(60)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private RedisTemplate<String, String> verify() {
        return org.mockito.Mockito.verify(redisTemplate);
    }
}
