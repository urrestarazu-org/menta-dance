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
        return RefreshToken.newFamily(USER_ID, TOKEN_HASH, 1L, NOW.plus(Duration.ofDays(7)));
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
    void mark_used_transitions_active_to_used_and_stamps_rotated_at() {
        RefreshToken token = active();

        token.markUsed();

        assertThat(token.getStatus()).isEqualTo(RefreshTokenStatus.USED);
        assertThat(token.getRotatedAt()).isNotNull();
        assertThat(token.isCompromised()).isTrue();
    }

    @Test
    void mark_used_rejects_a_token_that_is_not_active() {
        RefreshToken token = active();
        token.markUsed();

        assertThatThrownBy(token::markUsed)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ACTIVE");
    }

    @Test
    void mark_revoked_transitions_to_revoked_and_stamps_revoked_at() {
        RefreshToken token = active();

        token.markRevoked();

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isNotNull();
        assertThat(token.isCompromised()).isTrue();
    }

    @Test
    void mark_revoked_is_idempotent() {
        RefreshToken token = active();
        token.markRevoked();
        Instant firstRevokedAt = token.getRevokedAt();

        token.markRevoked();

        assertThat(token.getRevokedAt()).isEqualTo(firstRevokedAt);
    }

    @Test
    void is_expired_reflects_the_expiry_instant() {
        RefreshToken token = active();

        assertThat(token.isExpired(NOW)).isFalse();
        assertThat(token.isExpired(NOW.plus(Duration.ofDays(8)))).isTrue();
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
