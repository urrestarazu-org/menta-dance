package com.menta.auth.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.domain.model.PasswordResetToken;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordResetTokenJpaMapperTest {

    private static final UUID TOKEN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CREATED_AT = Instant.parse("2026-08-16T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-16T13:00:00Z");

    @Test
    void toJpaEntity_returns_null_for_a_null_token() {
        assertThat(PasswordResetTokenJpaMapper.toJpaEntity(null, null)).isNull();
    }

    @Test
    void toJpaEntity_maps_every_field_with_an_envelope() {
        PasswordResetToken token = PasswordResetToken.reconstitute(
            TOKEN_ID, UserId.of(USER_ID), "a".repeat(64), EXPIRES_AT, CREATED_AT, null, null
        );
        DeliveryEnvelope envelope = DeliveryEnvelope.of(new byte[] {1, 2, 3}, new byte[12], 1);

        PasswordResetTokenJpaEntity entity = PasswordResetTokenJpaMapper.toJpaEntity(token, envelope);

        assertThat(entity.getId()).isEqualTo(TOKEN_ID);
        assertThat(entity.getUserId()).isEqualTo(USER_ID);
        assertThat(entity.getTokenHash()).isEqualTo("a".repeat(64));
        assertThat(entity.getDeliveryCiphertext()).isEqualTo(envelope.getCiphertext());
        assertThat(entity.getDeliveryNonce()).isEqualTo(envelope.getNonce());
        assertThat(entity.getDeliveryKeyVersion()).isEqualTo((short) 1);
        assertThat(entity.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(entity.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void toJpaEntity_maps_a_null_envelope_to_null_delivery_fields() {
        PasswordResetToken token = PasswordResetToken.reconstitute(
            TOKEN_ID, UserId.of(USER_ID), "a".repeat(64), EXPIRES_AT, CREATED_AT, null, null
        );

        PasswordResetTokenJpaEntity entity = PasswordResetTokenJpaMapper.toJpaEntity(token, null);

        assertThat(entity.getDeliveryCiphertext()).isNull();
        assertThat(entity.getDeliveryNonce()).isNull();
        assertThat(entity.getDeliveryKeyVersion()).isNull();
    }

    @Test
    void toDomain_returns_null_for_a_null_entity() {
        assertThat(PasswordResetTokenJpaMapper.toDomain(null)).isNull();
    }

    @Test
    void toDomain_maps_every_field_back_to_the_aggregate() {
        PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity(
            TOKEN_ID, USER_ID, "a".repeat(64), null, null, null,
            EXPIRES_AT, CREATED_AT, null, null
        );

        PasswordResetToken domain = PasswordResetTokenJpaMapper.toDomain(entity);

        assertThat(domain.getId()).isEqualTo(TOKEN_ID);
        assertThat(domain.getUserId()).isEqualTo(UserId.of(USER_ID));
        assertThat(domain.getTokenHash()).isEqualTo("a".repeat(64));
        assertThat(domain.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(domain.getCreatedAt()).isEqualTo(CREATED_AT);
    }
}
