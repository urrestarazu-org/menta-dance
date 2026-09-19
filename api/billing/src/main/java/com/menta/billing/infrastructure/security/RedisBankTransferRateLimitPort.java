package com.menta.billing.infrastructure.security;

import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.domain.exception.BillingDegradedException;
import com.menta.billing.domain.model.PaymentId;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis-backed budgets for the bank-transfer flow (design C7): 10 subscription
 * creations/user/day and 3 proof uploads/payment/72h.
 *
 * <p>Two independent keys rather than one shared structure — the subjects (user vs. payment) and
 * windows (calendar day vs. the payment's own 72h lifetime) differ, and a shared structure would
 * tie the upload counter's expiry to the user's daily bucket.</p>
 *
 * <p>Reuses {@code RedisBillingPlansRateLimitPort}'s Lua script verbatim (single atomic
 * INCR+EXPIRE, one round trip) and fails the same way it does: an unreachable Redis must never
 * silently become "no limit".</p>
 */
public final class RedisBankTransferRateLimitPort implements BankTransferRateLimitPort {

    private static final String SUBSCRIPTION_CREATION_KEY_PREFIX = "rate:billing-bank-transfer:user:";
    private static final String PROOF_UPLOAD_KEY_PREFIX = "rate:billing-proof-upload:payment:";

    private static final RedisScript<List> CONSUME_SCRIPT = new DefaultRedisScript<>("""
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        local ttl = redis.call('TTL', KEYS[1])
        if count > tonumber(ARGV[2]) then
            return {0, ttl}
        end
        return {1, 0}
        """, List.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clock;
    private final long subscriptionCreationLimit;
    private final Duration subscriptionCreationWindow;
    private final long proofUploadLimit;
    private final Duration proofUploadWindow;

    public RedisBankTransferRateLimitPort(
        RedisTemplate<String, String> redisTemplate, Clock clock,
        long subscriptionCreationLimit, Duration subscriptionCreationWindow,
        long proofUploadLimit, Duration proofUploadWindow
    ) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate cannot be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock cannot be null");
        }
        validatePositive(subscriptionCreationLimit, subscriptionCreationWindow);
        validatePositive(proofUploadLimit, proofUploadWindow);
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.subscriptionCreationLimit = subscriptionCreationLimit;
        this.subscriptionCreationWindow = subscriptionCreationWindow;
        this.proofUploadLimit = proofUploadLimit;
        this.proofUploadWindow = proofUploadWindow;
    }

    @Override
    public RateLimitDecision consumeSubscriptionCreation(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        // The date suffix is the practical reset boundary (a new key each UTC day); the TTL below
        // is only a cleanup safety net for a key Redis would otherwise never evict.
        String today = LocalDate.ofInstant(clock.now(), ZoneOffset.UTC).toString();
        return consume(
            SUBSCRIPTION_CREATION_KEY_PREFIX + userId + ":" + today,
            subscriptionCreationLimit, subscriptionCreationWindow
        );
    }

    @Override
    public RateLimitDecision consumeProofUpload(PaymentId paymentId) {
        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId cannot be null");
        }
        return consume(PROOF_UPLOAD_KEY_PREFIX + paymentId, proofUploadLimit, proofUploadWindow);
    }

    private RateLimitDecision consume(String key, long limit, Duration window) {
        try {
            List<?> outcome = redisTemplate.execute(
                CONSUME_SCRIPT, List.of(key), String.valueOf(window.toSeconds()), String.valueOf(limit)
            );
            if (outcome == null || outcome.size() != 2
                || !(outcome.get(0) instanceof Number allowed)
                || !(outcome.get(1) instanceof Number ttl)) {
                throw new IllegalStateException("Invalid Redis bank-transfer rate-limit response");
            }
            if (allowed.longValue() == 1) {
                return RateLimitDecision.allowed();
            }
            return RateLimitDecision.limited(Duration.ofSeconds(Math.max(1, ttl.longValue())));
        } catch (RuntimeException exception) {
            // Fail closed: an unavailable throttle must never silently become no throttle.
            throw new BillingDegradedException(exception);
        }
    }

    private static void validatePositive(long limit, Duration window) {
        if (limit <= 0 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("limit and window must be positive");
        }
    }
}
