package com.menta.auth.infrastructure.persistence.mapper;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.domain.model.PasswordResetToken;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;

/** Keeps JPA delivery-envelope details outside the password-reset domain aggregate. */
public final class PasswordResetTokenJpaMapper {

    private PasswordResetTokenJpaMapper() {
    }

    /**
     * Maps the token to its JPA entity together with the encrypted delivery material.
     *
     * @param token the aggregate to map
     * @param envelope the delivery material to store in the same row; null leaves the delivery columns empty
     * @return the entity, or null when {@code token} is null
     */
    public static PasswordResetTokenJpaEntity toJpaEntity(
        PasswordResetToken token, DeliveryEnvelope envelope
    ) {
        if (token == null) {
            return null;
        }
        return new PasswordResetTokenJpaEntity(
            token.getId(), token.getUserId().getValue(), token.getTokenHash(),
            envelope == null ? null : envelope.getCiphertext(),
            envelope == null ? null : envelope.getNonce(),
            envelope == null ? null : (short) envelope.getKeyVersion(),
            token.getExpiresAt(), token.getCreatedAt(),
            token.getUsedAt(), token.getInvalidatedAt()
        );
    }

    /**
     * Rebuilds the domain token from a row, without the delivery envelope.
     *
     * @return the token, or null when {@code entity} is null
     */
    public static PasswordResetToken toDomain(PasswordResetTokenJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return PasswordResetToken.reconstitute(
            entity.getId(), UserId.of(entity.getUserId()), entity.getTokenHash(),
            entity.getExpiresAt(), entity.getCreatedAt(),
            entity.getUsedAt(), entity.getInvalidatedAt()
        );
    }
}
