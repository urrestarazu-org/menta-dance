package com.menta.auth.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.domain.model.PasswordResetToken;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.PasswordResetTokenJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenRepositoryAdapterTest {

    private static final UUID TOKEN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-16T13:00:00Z");

    @Mock private PasswordResetTokenJpaRepository repository;

    private PasswordResetTokenRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PasswordResetTokenRepositoryAdapter(repository);
    }

    @Test
    void save_maps_domain_and_envelope_to_entity_persists_and_returns_mapped_domain() {
        PasswordResetToken token = PasswordResetToken.reconstitute(
            TOKEN_ID, UserId.of(USER_ID), "a".repeat(64), EXPIRES_AT, NOW, null, null
        );
        DeliveryEnvelope envelope = DeliveryEnvelope.of(new byte[] {1, 2, 3}, new byte[12], 1);
        when(repository.save(any(PasswordResetTokenJpaEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PasswordResetToken saved = adapter.save(token, envelope);

        assertThat(saved.getId()).isEqualTo(TOKEN_ID);
    }

    @Test
    void findByHash_maps_the_entity_when_found() {
        PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity(
            TOKEN_ID, USER_ID, "a".repeat(64), null, null, null, EXPIRES_AT, NOW, null, null
        );
        when(repository.findByTokenHash("a".repeat(64))).thenReturn(Optional.of(entity));

        Optional<PasswordResetToken> found = adapter.findByHash("a".repeat(64));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(TOKEN_ID);
    }

    @Test
    void findByHash_returns_empty_when_not_found() {
        when(repository.findByTokenHash("a".repeat(64))).thenReturn(Optional.empty());

        Optional<PasswordResetToken> found = adapter.findByHash("a".repeat(64));

        assertThat(found).isEmpty();
    }

    @Test
    void consumeIfActive_returns_true_when_exactly_one_row_is_updated() {
        PasswordResetToken token = PasswordResetToken.reconstitute(
            TOKEN_ID, UserId.of(USER_ID), "a".repeat(64), EXPIRES_AT, NOW, null, null
        );
        when(repository.consumeIfActive(TOKEN_ID, NOW, NOW)).thenReturn(1);

        boolean consumed = adapter.consumeIfActive(token, NOW);

        assertThat(consumed).isTrue();
    }

    @Test
    void consumeIfActive_returns_false_when_no_row_is_updated() {
        PasswordResetToken token = PasswordResetToken.reconstitute(
            TOKEN_ID, UserId.of(USER_ID), "a".repeat(64), EXPIRES_AT, NOW, null, null
        );
        when(repository.consumeIfActive(TOKEN_ID, NOW, NOW)).thenReturn(0);

        boolean consumed = adapter.consumeIfActive(token, NOW);

        assertThat(consumed).isFalse();
    }

    @Test
    void clearDeliveryEnvelope_delegates_to_the_jpa_repository() {
        adapter.clearDeliveryEnvelope(TOKEN_ID);

        verify(repository).clearDeliveryEnvelope(TOKEN_ID);
    }

    @Test
    void invalidateActiveByUserId_delegates_to_the_jpa_repository() {
        adapter.invalidateActiveByUserId(UserId.of(USER_ID), NOW);

        verify(repository).invalidateActiveByUserId(USER_ID, NOW);
    }
}
