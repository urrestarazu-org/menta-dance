package com.menta.auth.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordResetTokenJpaEntityTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CREATED_AT = Instant.parse("2026-08-16T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-16T13:00:00Z");
    private static final Instant USED_AT = Instant.parse("2026-08-16T12:30:00Z");
    private static final Instant INVALIDATED_AT = Instant.parse("2026-08-16T12:45:00Z");

    @Test
    void exposes_every_field_supplied_to_the_full_constructor() {
        byte[] ciphertext = {1, 2, 3};
        byte[] nonce = new byte[12];

        PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity(
            ID, USER_ID, "a".repeat(64), ciphertext, nonce, (short) 1,
            EXPIRES_AT, CREATED_AT, USED_AT, INVALIDATED_AT
        );

        assertThat(entity.getId()).isEqualTo(ID);
        assertThat(entity.getUserId()).isEqualTo(USER_ID);
        assertThat(entity.getTokenHash()).isEqualTo("a".repeat(64));
        assertThat(entity.getDeliveryCiphertext()).isEqualTo(ciphertext);
        assertThat(entity.getDeliveryNonce()).isEqualTo(nonce);
        assertThat(entity.getDeliveryKeyVersion()).isEqualTo((short) 1);
        assertThat(entity.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(entity.getUsedAt()).isEqualTo(USED_AT);
        assertThat(entity.getInvalidatedAt()).isEqualTo(INVALIDATED_AT);
    }

    @Test
    void allows_null_delivery_envelope_fields() {
        PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity(
            ID, USER_ID, "b".repeat(64), null, null, null,
            EXPIRES_AT, CREATED_AT, null, null
        );

        assertThat(entity.getDeliveryCiphertext()).isNull();
        assertThat(entity.getDeliveryNonce()).isNull();
        assertThat(entity.getDeliveryKeyVersion()).isNull();
        assertThat(entity.getUsedAt()).isNull();
        assertThat(entity.getInvalidatedAt()).isNull();
    }
}
