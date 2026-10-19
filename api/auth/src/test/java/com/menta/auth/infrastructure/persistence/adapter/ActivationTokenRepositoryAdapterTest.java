package com.menta.auth.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.ActivationTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.ActivationTokenJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivationTokenRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final String HASH = "f".repeat(64);

    @Mock private ActivationTokenJpaRepository repository;
    private ActivationTokenRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ActivationTokenRepositoryAdapter(repository);
    }

    @Test
    void saves_the_envelope_with_its_token_and_maps_it_back_to_domain() {
        ActivationToken token = ActivationToken.issue(
            UserId.of(UUID.randomUUID()), HASH, NOW.plusSeconds(3600), NOW
        );
        DeliveryEnvelope envelope = DeliveryEnvelope.of(new byte[] {1, 2}, new byte[12], 2);
        when(repository.save(any(ActivationTokenJpaEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ActivationToken saved = adapter.save(token, envelope);

        ArgumentCaptor<ActivationTokenJpaEntity> entity = ArgumentCaptor.forClass(ActivationTokenJpaEntity.class);
        verify(repository).save(entity.capture());
        assertThat(entity.getValue().getDeliveryCiphertext()).containsExactly(1, 2);
        assertThat(entity.getValue().getDeliveryNonce()).hasSize(12);
        assertThat(entity.getValue().getDeliveryKeyVersion()).isEqualTo((short) 2);
        assertThat(saved).isEqualTo(token);
    }

    @Test
    void delegates_conditional_consumption_and_bulk_invalidation() {
        ActivationToken token = ActivationToken.issue(
            UserId.of(UUID.randomUUID()), HASH, NOW.plusSeconds(3600), NOW
        );
        when(repository.consumeIfActive(token.getId(), NOW, NOW)).thenReturn(1);

        assertThat(adapter.consumeIfActive(token, NOW)).isTrue();
        adapter.invalidateActiveByUserId(token.getUserId(), NOW);

        verify(repository).consumeIfActive(token.getId(), NOW, NOW);
        verify(repository).invalidateActiveByUserId(eq(token.getUserId().getValue()), eq(NOW));
    }

    @Test
    void maps_a_locked_hash_lookup_to_the_domain_aggregate() {
        ActivationToken token = ActivationToken.issue(
            UserId.of(UUID.randomUUID()), HASH, NOW.plusSeconds(3600), NOW
        );
        ActivationTokenJpaEntity row = new ActivationTokenJpaEntity(
            token.getId(), token.getUserId().getValue(), HASH, null, null, null,
            token.getExpiresAt(), token.getCreatedAt(), null, null
        );
        when(repository.findByTokenHash(HASH)).thenReturn(Optional.of(row));

        assertThat(adapter.findByHash(HASH)).contains(token);
    }
}
