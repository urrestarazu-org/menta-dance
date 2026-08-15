package com.menta.auth.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.infrastructure.persistence.entity.ActivationTokenJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@ContextConfiguration(classes = ActivationTokenJpaRepositoryTest.JpaConfiguration.class)
class ActivationTokenJpaRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Autowired private ActivationTokenJpaRepository repository;

    @Configuration
    @EntityScan(basePackageClasses = ActivationTokenJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = ActivationTokenJpaRepository.class)
    static class JpaConfiguration { }

    @Test
    void enforces_the_unique_token_hash_constraint() {
        repository.saveAndFlush(token(UUID.randomUUID(), HASH, NOW.plusSeconds(3600), null, null));

        assertThatThrownBy(() -> repository.saveAndFlush(
            token(UUID.randomUUID(), HASH, NOW.plusSeconds(3600), null, null)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidates_only_active_tokens_for_the_user() {
        UUID userId = UUID.randomUUID();
        ActivationTokenJpaEntity active = token(UUID.randomUUID(), "b".repeat(64), NOW.plusSeconds(3600), null, null);
        active.setUserId(userId);
        ActivationTokenJpaEntity expired = token(UUID.randomUUID(), "c".repeat(64), NOW.minusSeconds(1), null, null);
        expired.setUserId(userId);
        ActivationTokenJpaEntity used = token(UUID.randomUUID(), "d".repeat(64), NOW.plusSeconds(3600), NOW.minusSeconds(1), null);
        used.setUserId(userId);
        repository.saveAllAndFlush(java.util.List.of(active, expired, used));

        assertThat(repository.invalidateActiveByUserId(userId, NOW)).isEqualTo(1);

        assertThat(repository.findById(active.getId()).orElseThrow().getInvalidatedAt()).isEqualTo(NOW);
        assertThat(repository.findById(expired.getId()).orElseThrow().getInvalidatedAt()).isNull();
        assertThat(repository.findById(used.getId()).orElseThrow().getInvalidatedAt()).isNull();
    }

    @Test
    void clears_the_delivery_envelope_after_a_successful_delivery() {
        ActivationTokenJpaEntity token = repository.saveAndFlush(new ActivationTokenJpaEntity(
            UUID.randomUUID(), UUID.randomUUID(), "f".repeat(64), new byte[] {1, 2},
            new byte[12], (short) 1, NOW.plusSeconds(3600), NOW, null, null
        ));

        assertThat(repository.clearDeliveryEnvelope(token.getId())).isEqualTo(1);

        ActivationTokenJpaEntity cleared = repository.findById(token.getId()).orElseThrow();
        assertThat(cleared.getDeliveryCiphertext()).isNull();
        assertThat(cleared.getDeliveryNonce()).isNull();
        assertThat(cleared.getDeliveryKeyVersion()).isNull();
    }

    @Test
    void consumes_an_active_token_once_with_a_conditional_update() {
        ActivationTokenJpaEntity active = repository.saveAndFlush(
            token(UUID.randomUUID(), "e".repeat(64), NOW.plusSeconds(3600), null, null)
        );

        assertThat(repository.consumeIfActive(active.getId(), NOW, NOW)).isEqualTo(1);
        assertThat(repository.consumeIfActive(active.getId(), NOW.plusSeconds(1), NOW.plusSeconds(1))).isZero();
    }

    private ActivationTokenJpaEntity token(
        UUID id, String hash, Instant expiresAt, Instant usedAt, Instant invalidatedAt
    ) {
        return new ActivationTokenJpaEntity(
            id, UUID.randomUUID(), hash, null, null, null, expiresAt, NOW, usedAt, invalidatedAt
        );
    }
}
