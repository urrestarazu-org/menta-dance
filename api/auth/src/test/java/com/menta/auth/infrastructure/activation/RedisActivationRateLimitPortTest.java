package com.menta.auth.infrastructure.activation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.domain.exception.AuthDegradedException;
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
class RedisActivationRateLimitPortTest {

    private static final String EMAIL_FINGERPRINT = "a".repeat(64);
    private static final String CLIENT_FINGERPRINT = "b".repeat(64);

    @Mock private RedisTemplate<String, String> redisTemplate;
    private RedisActivationRateLimitPort port;

    @BeforeEach
    void setUp() {
        port = new RedisActivationRateLimitPort(redisTemplate, 3, 10, Duration.ofMinutes(15));
    }

    @Test
    void allows_when_the_atomic_script_reports_both_limits_below_threshold() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of(1L, 0L));

        assertThat(port.consume(EMAIL_FINGERPRINT, CLIENT_FINGERPRINT).isAllowed()).isTrue();

        verify(redisTemplate).execute(
            any(RedisScript.class),
            eq(List.of(
                "rate:auth-activation:email:{" + EMAIL_FINGERPRINT + "}",
                "rate:auth-activation:client:{" + CLIENT_FINGERPRINT + "}"
            )),
            eq("3"), eq("10"), eq("900")
        );
    }

    @Test
    void returns_retry_after_when_either_limit_is_exceeded() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenReturn(List.of(0L, 57L));

        assertThat(port.consume(EMAIL_FINGERPRINT, CLIENT_FINGERPRINT))
            .extracting(decision -> decision.getRetryAfter())
            .isEqualTo(Duration.ofSeconds(57));
    }

    @Test
    void fails_closed_when_redis_is_unavailable_without_retrying() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
            .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> port.consume(EMAIL_FINGERPRINT, CLIENT_FINGERPRINT))
            .isInstanceOf(AuthDegradedException.class);

        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
}
