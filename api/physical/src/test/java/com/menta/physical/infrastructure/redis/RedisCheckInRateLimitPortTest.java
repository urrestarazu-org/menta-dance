package com.menta.physical.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.menta.physical.application.dto.RateLimitDecision;
import com.menta.physical.application.dto.RateLimitDimension;
import com.menta.physical.domain.exception.CheckInDegradedException;
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
@SuppressWarnings({"unchecked", "rawtypes"})
class RedisCheckInRateLimitPortTest {

    private static final String CLIENT_FP = "a".repeat(64);
    private static final String DEVICE_FP = "b".repeat(64);
    private static final List<String> IP_KEYS = List.of("rate:physical-checkin:ip:{" + CLIENT_FP + "}");
    private static final List<String> DEVICE_KEYS =
        List.of("rate:physical-checkin:device:{" + DEVICE_FP + "}");

    @Mock private RedisTemplate<String, String> redisTemplate;

    private RedisCheckInRateLimitPort port;

    @BeforeEach
    void setUp() {
        port = new RedisCheckInRateLimitPort(redisTemplate, 120, 60, Duration.ofSeconds(60));
    }

    private void ipReplies(Object reply) {
        doReturn(reply).when(redisTemplate)
            .execute(any(RedisScript.class), eq(IP_KEYS), eq("60"), eq("120"));
    }

    private void deviceReplies(Object reply) {
        doReturn(reply).when(redisTemplate)
            .execute(any(RedisScript.class), eq(DEVICE_KEYS), eq("60"), eq("60"));
    }

    @Test
    void allows_when_both_budgets_have_room() {
        ipReplies(List.of(1L, 0L));
        deviceReplies(List.of(1L, 0L));

        assertThat(port.consume(CLIENT_FP, DEVICE_FP).isAllowed()).isTrue();
    }

    @Test
    void an_exhausted_ip_budget_limits_and_never_consumes_the_device_budget() {
        ipReplies(List.of(0L, 17L));

        RateLimitDecision decision = port.consume(CLIENT_FP, DEVICE_FP);

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getDimension()).isEqualTo(RateLimitDimension.IP);
        assertThat(decision.getRetryAfter()).isEqualTo(Duration.ofSeconds(17));
        verify(redisTemplate, never())
            .execute(any(RedisScript.class), eq(DEVICE_KEYS), eq("60"), eq("60"));
    }

    @Test
    void an_exhausted_device_budget_limits_by_device() {
        ipReplies(List.of(1L, 0L));
        deviceReplies(List.of(0L, 9L));

        RateLimitDecision decision = port.consume(CLIENT_FP, DEVICE_FP);

        assertThat(decision.getDimension()).isEqualTo(RateLimitDimension.DEVICE);
        assertThat(decision.getRetryAfter()).isEqualTo(Duration.ofSeconds(9));
    }

    @Test
    void a_missing_ttl_never_yields_a_retry_window_below_one_second() {
        ipReplies(List.of(0L, -1L));

        assertThat(port.consume(CLIENT_FP, DEVICE_FP).getRetryAfter())
            .isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void keys_hold_only_the_fingerprint_never_a_raw_value() {
        ipReplies(List.of(1L, 0L));
        deviceReplies(List.of(1L, 0L));

        port.consume(CLIENT_FP, DEVICE_FP);

        verify(redisTemplate).execute(any(RedisScript.class), eq(IP_KEYS), eq("60"), eq("120"));
        verify(redisTemplate).execute(any(RedisScript.class), eq(DEVICE_KEYS), eq("60"), eq("60"));
    }

    @Test
    void fails_closed_when_the_ip_script_raises() {
        doThrow(new IllegalStateException("redis down")).when(redisTemplate)
            .execute(any(RedisScript.class), eq(IP_KEYS), eq("60"), eq("120"));

        assertThatThrownBy(() -> port.consume(CLIENT_FP, DEVICE_FP))
            .isInstanceOf(CheckInDegradedException.class);
    }

    @Test
    void fails_closed_when_the_device_script_raises() {
        ipReplies(List.of(1L, 0L));
        doThrow(new IllegalStateException("redis down")).when(redisTemplate)
            .execute(any(RedisScript.class), eq(DEVICE_KEYS), eq("60"), eq("60"));

        assertThatThrownBy(() -> port.consume(CLIENT_FP, DEVICE_FP))
            .isInstanceOf(CheckInDegradedException.class);
    }

    @Test
    void fails_closed_on_a_null_reply() {
        ipReplies(null);

        assertThatThrownBy(() -> port.consume(CLIENT_FP, DEVICE_FP))
            .isInstanceOf(CheckInDegradedException.class);
    }

    @Test
    void fails_closed_on_a_reply_of_the_wrong_size() {
        ipReplies(List.of(1L));

        assertThatThrownBy(() -> port.consume(CLIENT_FP, DEVICE_FP))
            .isInstanceOf(CheckInDegradedException.class);
    }

    @Test
    void fails_closed_when_the_allowed_flag_is_not_a_number() {
        ipReplies(List.of("1", 0L));

        assertThatThrownBy(() -> port.consume(CLIENT_FP, DEVICE_FP))
            .isInstanceOf(CheckInDegradedException.class);
    }

    @Test
    void fails_closed_when_the_ttl_is_not_a_number() {
        ipReplies(List.of(0L, "17"));

        assertThatThrownBy(() -> port.consume(CLIENT_FP, DEVICE_FP))
            .isInstanceOf(CheckInDegradedException.class);
    }

    @Test
    void rejects_a_fingerprint_that_is_not_a_lowercase_sha_256() {
        assertThatThrownBy(() -> port.consume(null, DEVICE_FP))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> port.consume("RAW-IP", DEVICE_FP))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> port.consume(CLIENT_FP, "device-1"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_invalid_construction_arguments() {
        Duration window = Duration.ofSeconds(60);
        assertThatThrownBy(() -> new RedisCheckInRateLimitPort(null, 1, 1, window))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisCheckInRateLimitPort(redisTemplate, 0, 1, window))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisCheckInRateLimitPort(redisTemplate, 1, 0, window))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisCheckInRateLimitPort(redisTemplate, 1, 1, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisCheckInRateLimitPort(redisTemplate, 1, 1, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
            () -> new RedisCheckInRateLimitPort(redisTemplate, 1, 1, Duration.ofSeconds(-1))
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
