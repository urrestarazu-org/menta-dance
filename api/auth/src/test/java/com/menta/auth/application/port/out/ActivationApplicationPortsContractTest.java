package com.menta.auth.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.ActivationTokenStatus;
import com.menta.auth.domain.model.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract-shape tests for the account-activation application ports
 * (auth-account-activation design: "Puertos principales"). Each test proves
 * the interface is implementable end-to-end with a trivial fake before any
 * real infrastructure adapter exists (PR2 implements the production
 * adapters).
 */
class ActivationApplicationPortsContractTest {

    private static final Instant NOW = Instant.parse("2026-08-11T20:00:00Z");
    private static final UserId USER_ID = UserId.of(
        UUID.fromString("11111111-1111-1111-1111-111111111111")
    );

    @Test
    void repository_allows_save_lookup_and_bulk_invalidation() {
        ActivationTokenRepository repository = new InMemoryActivationTokenRepository();
        ActivationToken token = ActivationToken.issue(
            USER_ID, "a".repeat(64), NOW.plus(Duration.ofHours(24)), NOW
        );

        repository.save(token);

        assertThat(repository.findByHash("a".repeat(64))).contains(token);
        repository.invalidateActiveByUserId(USER_ID, NOW.plusSeconds(1));
        assertThat(repository.findByHash("a".repeat(64)))
            .map(t -> t.statusAt(NOW.plusSeconds(2)))
            .contains(ActivationTokenStatus.INVALIDATED);
    }

    @Test
    void repository_save_with_envelope_preserves_the_delivery_envelope() {
        InMemoryActivationTokenRepository repository = new InMemoryActivationTokenRepository();
        ActivationToken token = ActivationToken.issue(
            USER_ID, "b".repeat(64), NOW.plus(Duration.ofHours(24)), NOW
        );
        DeliveryEnvelope envelope =
            DeliveryEnvelope.of("secret".getBytes(StandardCharsets.UTF_8), new byte[12], 1);

        ActivationToken saved = repository.save(token, envelope);

        assertThat(saved).isEqualTo(token);
        assertThat(repository.findByHash("b".repeat(64))).contains(token);
        assertThat(repository.findEnvelopeByHash("b".repeat(64))).contains(envelope);
    }

    @Test
    void generator_and_hasher_compose_into_a_storable_digest() {
        ActivationTokenGenerator generator = () -> "raw-token-material";
        ActivationTokenHasher hasher = raw -> "h".repeat(64);

        String raw = generator.generate();

        assertThat(hasher.hash(raw)).hasSize(64);
    }

    @Test
    void delivery_cipher_round_trips_through_an_envelope() {
        ActivationDeliveryCipher cipher = new EchoDeliveryCipher();

        DeliveryEnvelope envelope = cipher.encrypt("recipient|raw-token");

        assertThat(cipher.decrypt(envelope)).isEqualTo("recipient|raw-token");
    }

    @Test
    void rate_limit_port_returns_a_decision_per_fingerprint_pair() {
        ActivationRateLimitPort rateLimit = (email, client) -> RateLimitDecision.allowed();

        assertThat(rateLimit.consume("email-fp", "client-fp").isAllowed()).isTrue();
    }

    @Test
    void outbox_contract_exposes_the_activation_event_type() {
        assertThat(AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED)
            .isEqualTo("auth.AccountActivationRequested");
    }

    /**
     * Minimal in-memory fake — proves the port is implementable, not
     * production code. {@code save(token, envelope)} is a real reference
     * implementation (not a compile-satisfying stub): it stores the
     * envelope in a second map keyed by the same token hash, so a test can
     * prove the envelope was actually preserved rather than silently
     * discarded (auth-account-activation review fix: the interface no
     * longer offers a default that drops it).
     */
    private static final class InMemoryActivationTokenRepository implements ActivationTokenRepository {
        private final Map<String, ActivationToken> byHash = new HashMap<>();
        private final Map<String, DeliveryEnvelope> envelopeByHash = new HashMap<>();

        @Override
        public ActivationToken save(ActivationToken token) {
            byHash.put(token.getTokenHash(), token);
            return token;
        }

        @Override
        public ActivationToken save(ActivationToken token, DeliveryEnvelope deliveryEnvelope) {
            byHash.put(token.getTokenHash(), token);
            envelopeByHash.put(token.getTokenHash(), deliveryEnvelope);
            return token;
        }

        @Override
        public Optional<ActivationToken> findByHash(String tokenHash) {
            return Optional.ofNullable(byHash.get(tokenHash));
        }

        @Override
        public boolean consumeIfActive(ActivationToken token, Instant now) {
            if (token.statusAt(now) != ActivationTokenStatus.ACTIVE) {
                return false;
            }
            token.consume(now);
            return true;
        }

        Optional<DeliveryEnvelope> findEnvelopeByHash(String tokenHash) {
            return Optional.ofNullable(envelopeByHash.get(tokenHash));
        }

        @Override
        public void clearDeliveryEnvelope(java.util.UUID tokenId) {
            envelopeByHash.entrySet().removeIf(entry ->
                byHash.get(entry.getKey()).getId().equals(tokenId)
            );
        }

        @Override
        public void invalidateActiveByUserId(UserId userId, Instant now) {
            byHash.values().stream()
                .filter(t -> t.getUserId().equals(userId))
                .filter(t -> t.statusAt(now) == ActivationTokenStatus.ACTIVE)
                .forEach(t -> t.invalidate(now));
        }
    }

    /** Minimal fake cipher — the real AES-GCM adapter lands in PR2. */
    private static final class EchoDeliveryCipher implements ActivationDeliveryCipher {
        @Override
        public DeliveryEnvelope encrypt(String plaintext) {
            return DeliveryEnvelope.of(
                plaintext.getBytes(StandardCharsets.UTF_8), new byte[12], 1
            );
        }

        @Override
        public String decrypt(DeliveryEnvelope envelope) {
            return new String(envelope.getCiphertext(), StandardCharsets.UTF_8);
        }
    }
}
