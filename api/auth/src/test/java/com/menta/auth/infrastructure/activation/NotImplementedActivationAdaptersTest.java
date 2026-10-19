package com.menta.auth.infrastructure.activation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Trivial compile-boundary check: every PR1 placeholder adapter fails loudly
 * instead of silently no-op'ing, so a missing PR2 rollout can never be
 * mistaken for working infrastructure. Real behavioral tests belong to PR2
 * (task 2.1/2.2/2.3/2.4), once genuine JPA/Redis/AES-GCM adapters replace
 * these classes.
 */
class NotImplementedActivationAdaptersTest {

    private static final UserId USER_ID = UserId.generate();
    private static final Instant NOW = Instant.parse("2026-08-11T20:00:00Z");
    private static final ActivationToken TOKEN =
        ActivationToken.issue(USER_ID, "a".repeat(64), NOW.plus(Duration.ofHours(24)), NOW);
    private static final DeliveryEnvelope ENVELOPE =
        DeliveryEnvelope.of("x".getBytes(StandardCharsets.UTF_8), new byte[12], 1);

    @Test
    void repository_throws_on_every_method() {
        NotImplementedActivationTokenRepository repository =
            new NotImplementedActivationTokenRepository();

        assertThatThrownBy(() -> repository.save(TOKEN))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repository.save(TOKEN, ENVELOPE))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repository.findByHash("hash"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repository.invalidateActiveByUserId(USER_ID, NOW))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rate_limit_port_throws() {
        NotImplementedActivationRateLimitPort rateLimit =
            new NotImplementedActivationRateLimitPort();

        assertThatThrownBy(() -> rateLimit.consume("email-fp", "client-fp"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void delivery_cipher_throws_on_both_operations() {
        NotImplementedActivationDeliveryCipher cipher =
            new NotImplementedActivationDeliveryCipher();

        assertThatThrownBy(() -> cipher.encrypt("plaintext"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> cipher.decrypt(ENVELOPE))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void generator_and_hasher_throw() {
        NotImplementedActivationTokenGenerator generator =
            new NotImplementedActivationTokenGenerator();
        NotImplementedActivationTokenHasher hasher = new NotImplementedActivationTokenHasher();

        assertThatThrownBy(generator::generate)
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> hasher.hash("raw"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
