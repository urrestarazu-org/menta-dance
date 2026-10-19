package com.menta.physical.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.menta.physical.domain.exception.CheckInDegradedException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisCheckInLockPortTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisCheckInLockPort port;

    @BeforeEach
    void setUp() {
        port = new RedisCheckInLockPort(redisTemplate);
    }

    @Test
    void returns_a_nonce_when_the_key_was_free() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("checkin:qr:jti-1"), any(), eq(Duration.ofSeconds(10))))
            .thenReturn(true);

        assertThat(port.acquireIfAbsent("checkin:qr:jti-1", Duration.ofSeconds(10))).isPresent();
    }

    @Test
    void returns_empty_when_another_holder_already_owns_the_key() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenReturn(false);

        assertThat(port.acquireIfAbsent("checkin:qr:jti-1", Duration.ofSeconds(10))).isEmpty();
    }

    @Test
    void treats_a_null_redis_reply_as_not_acquired() {
        // SET NX can reply nil through some Spring Data Redis serializer
        // configurations; never mistake that for a successful acquisition.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenReturn(null);

        assertThat(port.acquireIfAbsent("checkin:qr:jti-1", Duration.ofSeconds(10))).isEmpty();
    }

    @Test
    void fails_closed_when_redis_is_unavailable() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> port.acquireIfAbsent("checkin:qr:jti-1", Duration.ofSeconds(10)))
            .isInstanceOf(CheckInDegradedException.class);
    }

    @Test
    void rejects_a_null_or_blank_key() {
        assertThatThrownBy(() -> port.acquireIfAbsent(null, Duration.ofSeconds(10)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> port.acquireIfAbsent("  ", Duration.ofSeconds(10)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_non_positive_ttl() {
        assertThatThrownBy(() -> port.acquireIfAbsent("checkin:qr:jti-1", Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> port.acquireIfAbsent("checkin:qr:jti-1", Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> port.acquireIfAbsent("checkin:qr:jti-1", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_null_redis_template_at_construction() {
        assertThatThrownBy(() -> new RedisCheckInLockPort(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
