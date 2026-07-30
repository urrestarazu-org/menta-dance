package com.menta.auth.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.domain.model.RefreshToken;
import com.menta.auth.domain.model.RefreshTokenStatus;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.RefreshTokenJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RED-GREEN discipline: this test references RefreshTokenRepositoryAdapter
 * BEFORE 3.3 GREEN provides the impl, so it must not compile until the
 * adapter class exists.
 *
 * Tests cover the adapter's interaction with RefreshTokenJpaRepository:
 *   - findByHash: round-trips JPA entity → domain RefreshToken.
 *   - save: round-trips domain → JPA entity, returns mapped domain.
 *   - revokeFamily: delegates to bulk-update derived query.
 *   - findActiveOrUsedByFamily: delegates to derived query for ACTIVE|USED.
 *
 * Strict TDD: every assertion exercises the adapter (production code). The
 * mapper boundary is verified end-to-end through the roundtrip — a regression
 * in field mapping would surface as a failing test.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenRepositoryAdapterTest {

    private static final UUID REFRESH_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID FAMILY_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID USER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final String TOKEN_HASH = "abcdef".repeat(8) + "abcd"; // 64 chars
    private static final long TOKEN_VERSION = 3L;
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plusSeconds(7 * 86_400);

    @Mock private RefreshTokenJpaRepository jpaRepository;

    private RefreshTokenRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RefreshTokenRepositoryAdapter(jpaRepository);
    }

    @Nested
    @DisplayName("Spec: findByHash")
    class FindByHash {

        @Test
        void empty_when_repository_returns_empty() {
            when(jpaRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

            Optional<RefreshToken> result = adapter.findByHash(TOKEN_HASH);

            assertThat(result).isEmpty();
        }

        @Test
        void maps_entity_to_domain_when_repository_returns_one() {
            RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity(
                REFRESH_ID, FAMILY_ID, TOKEN_HASH, USER_ID,
                RefreshTokenStatus.ACTIVE, TOKEN_VERSION,
                EXPIRES_AT, NOW, null, null
            );
            when(jpaRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(entity));

            Optional<RefreshToken> result = adapter.findByHash(TOKEN_HASH);

            assertThat(result).isPresent();
            RefreshToken token = result.get();
            assertThat(token.getId()).isEqualTo(REFRESH_ID);
            assertThat(token.getFamilyId()).isEqualTo(FAMILY_ID);
            assertThat(token.getTokenHash()).isEqualTo(TOKEN_HASH);
            assertThat(token.getUserId()).isEqualTo(UserId.of(USER_ID));
            assertThat(token.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
            assertThat(token.getTokenVersion()).isEqualTo(TOKEN_VERSION);
            assertThat(token.getExpiresAt()).isEqualTo(EXPIRES_AT);
            assertThat(token.getCreatedAt()).isEqualTo(NOW);
            assertThat(token.getRotatedAt()).isNull();
            assertThat(token.getRevokedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("Spec: save")
    class Save {

        @Test
        void maps_domain_to_entity_persists_and_returns_mapped_domain() {
            RefreshToken domain = RefreshToken.newFamily(
                UserId.of(USER_ID), TOKEN_HASH, TOKEN_VERSION, EXPIRES_AT
            );
            when(jpaRepository.save(any(RefreshTokenJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

            RefreshToken saved = adapter.save(domain);

            // Roundtrip preserves identity: the saved row maps back to a domain
            // RefreshToken carrying the same UUID the factory minted.
            assertThat(saved.getId()).isEqualTo(domain.getId());
            assertThat(saved.getFamilyId()).isEqualTo(domain.getFamilyId());
            assertThat(saved.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
            assertThat(saved.getTokenHash()).isEqualTo(TOKEN_HASH);
            verify(jpaRepository, times(1)).save(any(RefreshTokenJpaEntity.class));
        }
    }

    @Nested
    @DisplayName("Spec: revokeFamily")
    class RevokeFamily {

        @Test
        void delegates_to_bulk_update_for_family_id_with_revokedAt_now() {
            when(jpaRepository.bulkRevokeByFamily(
                eq(RefreshTokenStatus.REVOKED), any(Instant.class), eq(FAMILY_ID)
            )).thenReturn(2);

            adapter.revokeFamily(FAMILY_ID);

            verify(jpaRepository, times(1)).bulkRevokeByFamily(
                eq(RefreshTokenStatus.REVOKED), any(Instant.class), eq(FAMILY_ID)
            );
        }
    }

    @Nested
    @DisplayName("Spec: findActiveOrUsedByFamily")
    class FindActiveOrUsedByFamily {

        @Test
        void returns_list_of_domain_tokens_when_repository_finds_them() {
            RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity(
                REFRESH_ID, FAMILY_ID, TOKEN_HASH, USER_ID,
                RefreshTokenStatus.ACTIVE, TOKEN_VERSION,
                EXPIRES_AT, NOW, null, null
            );
            when(jpaRepository.findByFamilyIdAndStatusIn(eq(FAMILY_ID), any()))
                .thenReturn(List.of(entity));

            List<RefreshToken> rows = adapter.findActiveOrUsedByFamily(FAMILY_ID);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getFamilyId()).isEqualTo(FAMILY_ID);
            assertThat(rows.get(0).getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
        }

        @Test
        void returns_empty_list_when_repository_finds_none() {
            when(jpaRepository.findByFamilyIdAndStatusIn(eq(FAMILY_ID), any()))
                .thenReturn(List.of());

            List<RefreshToken> rows = adapter.findActiveOrUsedByFamily(FAMILY_ID);

            assertThat(rows).isEmpty();
        }
    }

    }
