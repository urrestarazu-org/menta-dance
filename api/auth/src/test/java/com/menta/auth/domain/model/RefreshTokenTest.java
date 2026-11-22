package com.menta.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");
    private static final UserId USER_ID = UserId.of(UUID.randomUUID());
    private static final String TOKEN_HASH = "a".repeat(64);

    private static RefreshToken active() {
        return RefreshToken.newFamily(USER_ID, TOKEN_HASH, 1L, NOW.plus(Duration.ofDays(7)), NOW);
    }

    private static RefreshToken reconstitute(
        UUID id, UUID familyId, String tokenHash, UserId userId, RefreshTokenStatus status
    ) {
        return RefreshToken.reconstitute(
            id, familyId, tokenHash, userId, status, 1L,
            NOW.plus(Duration.ofDays(7)), NOW, null, null
        );
    }

    @Test
    void constructor_rejects_a_null_id() {
        assertThatThrownBy(() -> reconstitute(
            null, UUID.randomUUID(), TOKEN_HASH, USER_ID, RefreshTokenStatus.ACTIVE
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("id");
    }

    @Test
    void constructor_rejects_a_null_family_id() {
        assertThatThrownBy(() -> reconstitute(
            UUID.randomUUID(), null, TOKEN_HASH, USER_ID, RefreshTokenStatus.ACTIVE
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("familyId");
    }

    @Test
    void constructor_rejects_a_blank_token_hash() {
        assertThatThrownBy(() -> reconstitute(
            UUID.randomUUID(), UUID.randomUUID(), "  ", USER_ID, RefreshTokenStatus.ACTIVE
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tokenHash");
    }

    @Test
    void constructor_rejects_a_null_user_id() {
        assertThatThrownBy(() -> reconstitute(
            UUID.randomUUID(), UUID.randomUUID(), TOKEN_HASH, null, RefreshTokenStatus.ACTIVE
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userId");
    }

    @Test
    void constructor_rejects_a_null_status() {
        assertThatThrownBy(() -> reconstitute(
            UUID.randomUUID(), UUID.randomUUID(), TOKEN_HASH, USER_ID, null
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("status");
    }

    @Test
    void constructor_rejects_a_null_expires_at() {
        assertThatThrownBy(() -> RefreshToken.reconstitute(
            UUID.randomUUID(), UUID.randomUUID(), TOKEN_HASH, USER_ID, RefreshTokenStatus.ACTIVE,
            1L, null, NOW, null, null
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expiresAt");
    }

    @Test
    void new_family_stamps_created_at_from_the_given_instant() {
        RefreshToken token = active();

        assertThat(token.getCreatedAt()).isEqualTo(NOW);
        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(token.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
        assertThat(token.getRotatedAt()).isNull();
        assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    void rotate_mints_an_active_token_in_the_same_family_stamped_with_the_given_instant() {
        UUID familyId = UUID.randomUUID();
        Instant expiresAt = NOW.plus(Duration.ofDays(7));

        RefreshToken token = RefreshToken.rotate(USER_ID, familyId, TOKEN_HASH, 2L, expiresAt, NOW);

        assertThat(token.getFamilyId()).isEqualTo(familyId);
        assertThat(token.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
        assertThat(token.getTokenVersion()).isEqualTo(2L);
        assertThat(token.getCreatedAt()).isEqualTo(NOW);
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(token.getRotatedAt()).isNull();
        assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    void mark_used_transitions_active_to_used_and_stamps_rotated_at_with_the_given_instant() {
        RefreshToken token = active();
        Instant usedAt = NOW.plusSeconds(90);

        token.markUsed(usedAt);

        assertThat(token.getStatus()).isEqualTo(RefreshTokenStatus.USED);
        assertThat(token.getRotatedAt()).isEqualTo(usedAt);
        assertThat(token.isCompromised()).isTrue();
    }

    @Test
    void mark_used_rejects_a_token_that_is_not_active() {
        RefreshToken token = active();
        token.markUsed(NOW);

        assertThatThrownBy(() -> token.markUsed(NOW.plusSeconds(1)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ACTIVE");
        assertThat(token.getRotatedAt()).isEqualTo(NOW);
    }

    @Test
    void mark_revoked_transitions_to_revoked_and_stamps_revoked_at_with_the_given_instant() {
        RefreshToken token = active();
        Instant revokedAt = NOW.plusSeconds(120);

        token.markRevoked(revokedAt);

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(revokedAt);
        assertThat(token.isCompromised()).isTrue();
    }

    @Test
    void mark_revoked_is_idempotent_and_keeps_the_original_revoked_at() {
        RefreshToken token = active();
        Instant firstRevokedAt = NOW.plusSeconds(120);
        token.markRevoked(firstRevokedAt);

        token.markRevoked(NOW.plusSeconds(600));

        assertThat(token.getRevokedAt()).isEqualTo(firstRevokedAt);
    }

    @Test
    void is_expired_reflects_the_expiry_instant() {
        RefreshToken token = active();
        Instant expiresAt = token.getExpiresAt();

        assertThat(token.isExpired(NOW)).isFalse();
        assertThat(token.isExpired(expiresAt.minusMillis(1))).isFalse();
        assertThat(token.isExpired(expiresAt)).isTrue();
        assertThat(token.isExpired(expiresAt.plusMillis(1))).isTrue();
    }

    @Test
    void equal_when_ids_match() {
        RefreshToken token = active();
        RefreshToken same = reconstitute(
            token.getId(), token.getFamilyId(), TOKEN_HASH, USER_ID, RefreshTokenStatus.ACTIVE
        );

        assertThat(token).isEqualTo(same);
        assertThat(token).hasSameHashCodeAs(same);
    }

    @Test
    void not_equal_to_null_a_different_type_or_a_different_id() {
        RefreshToken token = active();

        assertThat(token).isEqualTo(token);
        assertThat(token).isNotEqualTo(null);
        assertThat(token).isNotEqualTo("not-a-refresh-token");
        assertThat(token).isNotEqualTo(active());
    }

    @Test
    void to_string_reports_identity_and_status_without_the_token_hash() {
        RefreshToken token = active();

        assertThat(token.toString())
            .contains("id=" + token.getId())
            .contains("familyId=" + token.getFamilyId())
            .contains("status=ACTIVE")
            .doesNotContain(TOKEN_HASH);
    }
}
