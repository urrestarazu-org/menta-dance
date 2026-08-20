package com.menta.auth.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.domain.model.RefreshTokenStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenJpaEntityTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FAMILY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant CREATED_AT = Instant.parse("2026-08-16T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-23T12:00:00Z");
    private static final Instant ROTATED_AT = Instant.parse("2026-08-17T12:00:00Z");
    private static final Instant REVOKED_AT = Instant.parse("2026-08-18T12:00:00Z");

    @Test
    void exposes_every_field_supplied_to_the_full_constructor() {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity(
            ID, FAMILY_ID, "a".repeat(64), USER_ID, RefreshTokenStatus.ACTIVE, 2L,
            EXPIRES_AT, CREATED_AT, ROTATED_AT, REVOKED_AT
        );

        assertThat(entity.getId()).isEqualTo(ID);
        assertThat(entity.getFamilyId()).isEqualTo(FAMILY_ID);
        assertThat(entity.getTokenHash()).isEqualTo("a".repeat(64));
        assertThat(entity.getUserId()).isEqualTo(USER_ID);
        assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
        assertThat(entity.getTokenVersion()).isEqualTo(2L);
        assertThat(entity.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(entity.getRotatedAt()).isEqualTo(ROTATED_AT);
        assertThat(entity.getRevokedAt()).isEqualTo(REVOKED_AT);
    }

    @Test
    void every_setter_mutates_its_matching_field() {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity(
            ID, FAMILY_ID, "a".repeat(64), USER_ID, RefreshTokenStatus.ACTIVE, 2L,
            EXPIRES_AT, CREATED_AT, null, null
        );

        UUID newId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID newFamilyId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID newUserId = UUID.fromString("66666666-6666-6666-6666-666666666666");

        entity.setId(newId);
        entity.setFamilyId(newFamilyId);
        entity.setTokenHash("b".repeat(64));
        entity.setUserId(newUserId);
        entity.setStatus(RefreshTokenStatus.REVOKED);
        entity.setTokenVersion(3L);
        entity.setExpiresAt(EXPIRES_AT.plusSeconds(1));
        entity.setCreatedAt(CREATED_AT.plusSeconds(1));
        entity.setRotatedAt(ROTATED_AT);
        entity.setRevokedAt(REVOKED_AT);

        assertThat(entity.getId()).isEqualTo(newId);
        assertThat(entity.getFamilyId()).isEqualTo(newFamilyId);
        assertThat(entity.getTokenHash()).isEqualTo("b".repeat(64));
        assertThat(entity.getUserId()).isEqualTo(newUserId);
        assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
        assertThat(entity.getTokenVersion()).isEqualTo(3L);
        assertThat(entity.getExpiresAt()).isEqualTo(EXPIRES_AT.plusSeconds(1));
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT.plusSeconds(1));
        assertThat(entity.getRotatedAt()).isEqualTo(ROTATED_AT);
        assertThat(entity.getRevokedAt()).isEqualTo(REVOKED_AT);
    }
}
