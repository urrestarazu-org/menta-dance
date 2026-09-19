package com.menta.billing.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.domain.exception.BillingDegradedException;
import com.menta.billing.domain.model.PaymentId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Mirrors {@code RedisBillingPlansRateLimitPortTest} (design C7): reuses the same Lua script
 * verbatim, but proves the two independent keys/limits/windows for bank-transfer creation and
 * proof-upload budgets.
 */
@ExtendWith(MockitoExtension.class)
class RedisBankTransferRateLimitPortTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final PaymentId PAYMENT_ID =
        PaymentId.of("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
    private static final String SUBSCRIPTION_KEY = "rate:billing-bank-transfer:user:" + USER_ID + ":2026-09-18";
    private static final String PROOF_KEY = "rate:billing-proof-upload:payment:" + PAYMENT_ID;

    @Mock private RedisTemplate<String, String> redisTemplate;
    private Clock clock;
    private RedisBankTransferRateLimitPort port;

    @BeforeEach
    void setUp() {
        clock = () -> NOW;
        port = new RedisBankTransferRateLimitPort(
            redisTemplate, clock, 10, Duration.ofHours(26), 3, Duration.ofHours(72)
        );
    }

    @Test
    void subscription_creation_allows_while_the_daily_budget_is_below_threshold() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of(1L, 0L));

        assertThat(port.consumeSubscriptionCreation(USER_ID).isAllowed()).isTrue();

        verify().execute(any(RedisScript.class), eq(List.of(SUBSCRIPTION_KEY)), eq("93600"), eq("10"));
    }

    @Test
    void subscription_creation_reports_the_remaining_window_when_the_daily_budget_is_spent() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of(0L, 5400L));

        assertThat(port.consumeSubscriptionCreation(USER_ID).getRetryAfter()).isEqualTo(Duration.ofSeconds(5400));
    }

    @Test
    void proof_upload_allows_while_the_per_payment_budget_is_below_threshold() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of(1L, 0L));

        assertThat(port.consumeProofUpload(PAYMENT_ID).isAllowed()).isTrue();

        verify().execute(any(RedisScript.class), eq(List.of(PROOF_KEY)), eq("259200"), eq("3"));
    }

    @Test
    void proof_upload_reports_the_remaining_window_when_the_per_payment_budget_is_spent() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of(0L, 3600L));

        assertThat(port.consumeProofUpload(PAYMENT_ID).getRetryAfter()).isEqualTo(Duration.ofSeconds(3600));
    }

    @Test
    void never_reports_a_zero_retry_after_for_a_limited_decision() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of(0L, 0L));

        assertThat(port.consumeSubscriptionCreation(USER_ID).getRetryAfter()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void fails_closed_when_redis_is_unavailable_for_either_budget() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> port.consumeSubscriptionCreation(USER_ID))
            .isInstanceOf(BillingDegradedException.class);
        assertThatThrownBy(() -> port.consumeProofUpload(PAYMENT_ID))
            .isInstanceOf(BillingDegradedException.class);
    }

    @Test
    void fails_closed_on_a_malformed_script_response() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of(1L));

        assertThatThrownBy(() -> port.consumeSubscriptionCreation(USER_ID))
            .isInstanceOf(BillingDegradedException.class);
    }

    @Test
    void rejects_a_null_user_or_payment_id() {
        assertThatThrownBy(() -> port.consumeSubscriptionCreation(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> port.consumeProofUpload(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_non_positive_limit_or_window_at_construction() {
        assertThatThrownBy(() -> new RedisBankTransferRateLimitPort(
            redisTemplate, clock, 0, Duration.ofHours(26), 3, Duration.ofHours(72)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisBankTransferRateLimitPort(
            redisTemplate, clock, 10, Duration.ZERO, 3, Duration.ofHours(72)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisBankTransferRateLimitPort(
            redisTemplate, clock, 10, Duration.ofHours(26), 0, Duration.ofHours(72)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisBankTransferRateLimitPort(
            redisTemplate, clock, 10, Duration.ofHours(26), 3, Duration.ZERO
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisBankTransferRateLimitPort(
            null, clock, 10, Duration.ofHours(26), 3, Duration.ofHours(72)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisBankTransferRateLimitPort(
            redisTemplate, null, 10, Duration.ofHours(26), 3, Duration.ofHours(72)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private RedisTemplate<String, String> verify() {
        return org.mockito.Mockito.verify(redisTemplate);
    }
}
